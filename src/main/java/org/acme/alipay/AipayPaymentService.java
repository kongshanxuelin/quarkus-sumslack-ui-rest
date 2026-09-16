package org.acme.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布应用「按量付费」支付服务（电脑网站支付 / alipay.trade.page.pay）。
 * <p>
 * 流程：
 * 1) 上传接口命中 402 门控 → createPayOrder() 创建电脑网站支付订单，返回 outTradeNo + 支付宝收银台 HTML 表单；
 * 2) 前端将表单提交到支付宝 → 用户在支付宝收银台完成支付；
 * 3) 支付宝异步回调 /api/alipay/notify → handleNotify() 验签并标记已支付、签发服务端 proof；
 * 4) agent 轮询 /api/file/order?orderId= 拿到 proof，再用 Payment-Proof 头复用同一次上传；
 * 5) validateProof() 校验通过后放行解压发布。
 * <p>
 * 注意：支付宝密钥复用 AlipayAipayAgentPaymentVerify.getAlipayConfig()（单一来源，勿两处各写一份）。
 * 生产环境请把私钥/支付宝公钥移到 application.properties 或密钥管理，并以公网 https 作为 notify 地址。
 */
@ApplicationScoped
public class AipayPaymentService {

    private static final Logger LOG = Logger.getLogger(AipayPaymentService.class);

    /** 支付成功后的异步通知地址。生产必须为公网可访问的 https 地址。 */
    private static final String NOTIFY_URL = "http://localhost:9090/api/alipay/notify";

    /**
     * 服务端自签「支付凭证(proof)」的密钥，独立于支付宝密钥。
     * 生产请改为强随机值并通过配置注入，切勿提交到代码库。
     */
    private static final String SERVER_SECRET = "CHANGE_ME_server_proof_secret_32bytes_min";

    private static final String SUBJECT = "发布应用到线上";

    // 复用 AlipayAipayAgentPaymentVerify 里的 AlipayConfig（appId / 私钥 / 支付宝公钥 / 网关）
    private final AlipayConfig cfg = AlipayAipayAgentPaymentVerify.getAlipayConfig();
    private final AlipayClient client = new DefaultAlipayClient(cfg);
    private final String alipayPublicKey = cfg.getAlipayPublicKey();

    /** 订单存储：key = outTradeNo。生产请换 DB/Redis（需支持按 outTradeNo 查询与并发更新）。 */
    private final Map<String, OrderRecord> orders = new ConcurrentHashMap<>();

    public AipayPaymentService() throws AlipayApiException {
    }

    /** 生成商户订单号。生产建议加随机后缀/防重，并保证唯一。 */
    private static String newOutTradeNo() {
        return "PUB" + Instant.now().toEpochMilli();
    }


    /**
     * 创建电脑网站支付订单，返回商户订单号、支付宝收银台跳转 URL（可生成二维码）和 HTML 表单。
     * - qrCode：支付宝收银台 URL，agent/前端可用在线 QR 生成器（如 https://api.qrserver.com/v1/create-qr-code/?data=URL）生成二维码让用户扫码支付；
     * - payForm：完整 HTML 表单，浏览器渲染后自动提交跳转到支付宝收银台。
     */
    public PayOrder createPayOrder() throws AlipayApiException {
        String outTradeNo = newOutTradeNo();

        AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount("0.01");
        model.setSubject(SUBJECT);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        req.setNotifyUrl(NOTIFY_URL);
        req.setReturnUrl(NOTIFY_URL);
        req.setBizModel(model);

        // pageExecute(req, "GET") 返回支付宝收银台跳转 URL（可用于生成二维码）
        // pageExecute(req)        返回完整 HTML 表单（浏览器渲染后自动提交）
        String payUrl = client.pageExecute(req, "GET").getBody();
        String payForm = client.pageExecute(req).getBody();
        orders.put(outTradeNo, new OrderRecord(outTradeNo, OrderStatus.UNPAID, null, null, payUrl, payForm));
        LOG.infof("已创建电脑网站支付订单 %s", outTradeNo);
        return new PayOrder(outTradeNo, payUrl, payForm);
    }

    /** 支付宝异步通知：验签 → 校验交易状态 → 标记已支付并签发 proof。返回 true 表示已正确受理（回写 success）。 */
    public boolean handleNotify(Map<String, String> params) {
        try {
            boolean signOk = AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
            if (!signOk) {
                LOG.warn("支付宝异步通知签名校验失败");
                return false;
            }
            String tradeStatus = params.get("trade_status");
            if (!("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))) {
                // WAIT_BUYER_PAY 等中间态：返回 success 让支付宝停止重试，待支付成功会再次通知
                return true;
            }
            String outTradeNo = params.get("out_trade_no");
            OrderRecord rec = orders.get(outTradeNo);
            if (rec == null) {
                LOG.warnf("通知对应的订单不存在: %s（可能非本服务创建）", outTradeNo);
                return false;
            }
            long paidAt = Instant.now().getEpochSecond();
            rec.paidAt = paidAt;
            rec.status = OrderStatus.PAID;
            rec.proof = signProof(outTradeNo, paidAt);
            LOG.infof("订单 %s 已支付，签发 proof", outTradeNo);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "处理支付宝异步通知异常");
            return false;
        }
    }

    /** 查询订单状态（供 agent 轮询）。已支付时一并返回服务端 proof；未支付时返回二维码/表单。 */
    public OrderView queryOrder(String outTradeNo) throws AlipayApiException {
        OrderRecord rec = orders.get(outTradeNo);
        if (rec != null && rec.status == OrderStatus.PAID) {
            return new OrderView(outTradeNo, true, rec.proof, null, null);
        }
        // 本地未标记已付，主动查支付宝确认
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        req.setBizModel(model);
        AlipayTradeQueryResponse resp = client.execute(req);

        // ACQ.TRADE_NOT_EXIST 是正常业务场景（订单已创建但用户尚未提交支付），不应视为错误
        if (!resp.isSuccess() && "ACQ.TRADE_NOT_EXIST".equals(resp.getSubCode())) {
            LOG.debugf("订单 %s 在支付宝尚未创建交易（用户未提交支付），返回未支付状态", outTradeNo);
            String qr = rec != null ? rec.qrCode : null;
            String form = rec != null ? rec.payForm : null;
            return new OrderView(outTradeNo, false, null, qr, form);
        }

        if (resp.isSuccess()
                && ("TRADE_SUCCESS".equals(resp.getTradeStatus()) || "TRADE_FINISHED".equals(resp.getTradeStatus()))) {
            long paidAt = Instant.now().getEpochSecond();
            OrderRecord r = orders.computeIfAbsent(outTradeNo, k -> new OrderRecord(outTradeNo, OrderStatus.UNPAID, null, null, null, null));
            r.status = OrderStatus.PAID;
            r.paidAt = paidAt;
            r.proof = signProof(outTradeNo, paidAt);
            return new OrderView(outTradeNo, true, r.proof, null, null);
        }
        // 未支付：返回二维码/表单供 agent 继续展示
        String qr = rec != null ? rec.qrCode : null;
        String form = rec != null ? rec.payForm : null;
        return new OrderView(outTradeNo, false, null, qr, form);
    }

    /**
     * 校验 agent 带回的支付凭证(proof)。有效且已支付则返回 outTradeNo，否则返回 null。
     * proof 格式：<outTradeNo>.<hmac>，hmac = base64url(HmacSHA256(outTradeNo + "." + paidAt, SERVER_SECRET))。
     */
    public String validateProof(String proof) {
        if (proof == null || proof.isEmpty()) return null;
        int dot = proof.lastIndexOf('.');
        if (dot <= 0) return null;
        String outTradeNo = proof.substring(0, dot);
        String hmac = proof.substring(dot + 1);
        OrderRecord rec = orders.get(outTradeNo);
        if (rec == null || rec.status != OrderStatus.PAID || rec.paidAt == null) return null;
        String expected = hmacSha256(outTradeNo + "." + rec.paidAt, SERVER_SECRET);
        if (!constantTimeEquals(expected, hmac)) return null;
        return outTradeNo;
    }

    private String signProof(String outTradeNo, long paidAt) {
        return outTradeNo + "." + hmacSha256(outTradeNo + "." + paidAt, SERVER_SECRET);
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ba.length; i++) r |= ba[i] ^ bb[i];
        return r == 0;
    }

    enum OrderStatus { UNPAID, PAID }

    static class OrderRecord {
        final String outTradeNo;
        OrderStatus status;
        Long paidAt;
        String proof;
        String qrCode;
        String payForm;
        OrderRecord(String outTradeNo, OrderStatus status, Long paidAt, String proof, String qrCode, String payForm) {
            this.outTradeNo = outTradeNo;
            this.status = status;
            this.paidAt = paidAt;
            this.proof = proof;
            this.qrCode = qrCode;
            this.payForm = payForm;
        }
    }

    /** 创建订单的返回：商户订单号 + 支付宝收银台 URL（用于生成二维码）+ HTML 表单（浏览器直接提交）。 */
    public static class PayOrder {
        public final String outTradeNo;
        public final String qrCode;
        public final String payForm;
        PayOrder(String outTradeNo, String qrCode, String payForm) {
            this.outTradeNo = outTradeNo;
            this.qrCode = qrCode;
            this.payForm = payForm;
        }
    }

    /** 查询订单的返回：订单号 + 是否已支付 + （已支付时）服务端支付凭证 + （未支付时）支付二维码/表单。 */
    public static class OrderView {
        public final String orderId;
        public final boolean paid;
        public final String proof;
        public final String qrCode;
        public final String payForm;
        OrderView(String orderId, boolean paid, String proof, String qrCode, String payForm) {
            this.orderId = orderId; this.paid = paid; this.proof = proof; this.qrCode = qrCode; this.payForm = payForm;
        }
    }
}
