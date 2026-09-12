package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.service.FileExtractorService;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.util.HashMap;
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

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(
            @RestHeader("token") String token,
            @RestForm("file") FileUpload file) {

        // 1. 校验 token
        if (token == null || !REQUIRED_TOKEN.equals(token)) {
            return errorResponse(Response.Status.UNAUTHORIZED, "AUTH_FAILED", "无效的 token");
        }

        // 2. 校验文件
        if (file == null || file.size() == 0) {
            return errorResponse(Response.Status.BAD_REQUEST, "NO_FILE", "请上传文件");
        }

        String fileName = file.fileName();
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".zip") && !lowerName.endsWith(".tar.gz") && !lowerName.endsWith(".tgz")) {
            return errorResponse(Response.Status.BAD_REQUEST, "INVALID_FORMAT",
                    "不支持的文件格式，仅支持 .zip 和 .tar.gz");
        }

        // 3. 读取文件字节并解压
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
