package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.alipay.AipayPaymentService;
import org.acme.service.FileExtractorService;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.spi.ServerRequestContext;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传接口
 * <p>
 * POST /api/file/upload
 * <p>
 * Header: token=sumslack（必须）
 * <p>
 * Body: multipart/form-data，字段名 file，仅支持 .zip 和 .tar.gz 格式。
 * <p>
 * 解压到 /var/www/cus/ 目录下，若目标目录已存在则返回失败。
 */
@Path("/api/file")
@Produces(MediaType.APPLICATION_JSON)
public class FileUploadResource {

    private static final Logger LOG = Logger.getLogger(FileUploadResource.class);

    /**
     * 认证 Token 值
     */
    private static final String REQUIRED_TOKEN = "sumslack";

    @Inject
    FileExtractorService extractorService;

    @Inject
    AipayPaymentService paymentService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(
            @Context ServerRequestContext context,
            @RestHeader("token") String token,
            @RestForm("file") FileUpload file) {
        // 1. 鉴权（下单前也要鉴权，避免匿名触发支付订单）
        if (token == null || !REQUIRED_TOKEN.equals(token)) {
            return errorResponse(Response.Status.UNAUTHORIZED, "AUTH_FAILED", "无效的 token");
        }

        // 2. 支付门控：携带有效 Payment-Proof 才放行；否则创建电脑网站支付订单并返回支付入口
        HttpHeaders headers = context.getRequestHeaders();
        String proof = headers.getHeaderString("Payment-Proof");
        String orderId = (proof != null && !proof.isEmpty()) ? paymentService.validateProof(proof) : null;
        if (orderId == null) {
            try {
                AipayPaymentService.PayOrder order = paymentService.createPayOrder();
                Map<String, Object> body = new HashMap<>();
                body.put("orderId", order.outTradeNo);
                body.put("amount", 0.01);
                // qrCode 为支付宝收银台跳转 URL，可用于生成二维码让用户扫码支付
                //（也可用在线 QR 生成器：https://api.qrserver.com/v1/create-qr-code/?data=<URL>）
                body.put("qrCode", order.qrCode);
                // payForm 为完整 HTML 表单，浏览器渲染后自动提交跳转到支付宝收银台
                body.put("payForm", order.payForm);
                body.put("message", "请扫码或点击链接支付 0.01 元，支付完成后用返回的支付凭证(Payment-Proof)重试上传");
                return Response.status(402)
                        .type("application/json;charset=UTF-8")
                        .entity(body)
                        .build();
            } catch (Exception e) {
                LOG.errorf(e, "创建支付订单失败");
                return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "PAY_CREATE_FAILED",
                        "创建支付订单失败: " + e.getMessage());
            }
        }

        // 3. 校验文件
        if (file == null || file.size() == 0) {
            return errorResponse(Response.Status.BAD_REQUEST, "NO_FILE", "请上传文件");
        }

        String fileName = file.fileName();
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".zip") && !lowerName.endsWith(".tar.gz") && !lowerName.endsWith(".tgz")) {
            return errorResponse(Response.Status.BAD_REQUEST, "INVALID_FORMAT",
                    "不支持的文件格式，仅支持 .zip 和 .tar.gz");
        }

        // 4. 读取文件字节并解压
        try {
            java.nio.file.Path tempFile = file.filePath();
            byte[] fileData = Files.readAllBytes(tempFile);
            java.nio.file.Path targetDir = extractorService.extract(fileName, fileData);

            Map<String, Object> data = new HashMap<>();
            data.put("status", 200);
            data.put("success", true);
            data.put("message", "上传解压成功");
            data.put("targetDir", targetDir.toString());

            return Response.ok(data).build();

        } catch (IllegalArgumentException e) {
            LOG.warnf("上传失败: %s", e.getMessage());
            return errorResponse(Response.Status.CONFLICT, "EXTRACT_FAILED", e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "上传解压异常");
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "EXTRACT_ERROR",
                    "解压失败: " + e.getMessage());
        }
    }

    /**
     * 查询支付订单状态（供 agent 轮询）。已支付时一并返回服务端支付凭证 proof。
     * GET /api/file/order?orderId=PUBxxxx
     */
    @GET
    @Path("/order")
    public Response getOrder(@RestQuery("orderId") String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return errorResponse(Response.Status.BAD_REQUEST, "NO_ORDER", "缺少 orderId");
        }
        try {
            AipayPaymentService.OrderView v = paymentService.queryOrder(orderId);
            Map<String, Object> data = new HashMap<>();
            data.put("orderId", v.orderId);
            data.put("paid", v.paid);
            if (v.paid && v.proof != null) data.put("proof", v.proof);
            if (!v.paid) {
                if (v.qrCode != null) data.put("qrCode", v.qrCode);
                if (v.payForm != null) data.put("payForm", v.payForm);
                data.put("amount", 0.01);
            }
            return Response.ok(data).build();
        } catch (Exception e) {
            LOG.errorf(e, "查询订单失败");
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "ORDER_QUERY_FAILED",
                    "查询订单失败: " + e.getMessage());
        }
    }

    /**
     * 构造统一错误响应
     */
    private Response errorResponse(Response.Status status, String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.getStatusCode());
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        return Response.status(status).entity(body).build();
    }
}
