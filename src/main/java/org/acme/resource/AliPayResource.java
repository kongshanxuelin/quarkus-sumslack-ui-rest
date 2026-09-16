package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.alipay.AipayPaymentService;
import org.jboss.logging.Logger;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝异步通知接收端点。
 * <p>
 * 路径：POST /api/alipay/notify
 * 必须与 AipayPaymentService.NOTIFY_URL 一致（当面付下单时写入 notify_url）。
 * 支付宝以 application/x-www-form-urlencoded 回调，本端点读取原始 body 自行解析并验签。
 */
@Path("/api/alipay")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
public class AliPayResource {

    private static final Logger LOG = Logger.getLogger(AliPayResource.class);

    @Inject
    AipayPaymentService paymentService;

    @POST
    @Path("/notify")
    public Response notify(String body) {
        Map<String, String> params = parseForm(body);
        boolean ok = paymentService.handleNotify(params);
        // 支付宝以收到文本 "success" 视为已正确处理；其余内容会按策略重试
        return Response.ok(ok ? "success" : "failure").build();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String kv : body.split("&")) {
            int idx = kv.indexOf('=');
            if (idx < 0) {
                map.put(decode(kv), "");
            } else {
                map.put(decode(kv.substring(0, idx)), decode(kv.substring(idx + 1)));
            }
        }
        return map;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
