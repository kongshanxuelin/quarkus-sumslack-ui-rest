package org.acme.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.dto.ExecRequest;
import org.acme.dto.ExecResponse;
import org.acme.service.ScriptExecutor;

import java.nio.file.Files;
import java.nio.file.Paths;

@Path("/gw-nb/sdep-gateway-api/api/exec")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScriptExecResource {

    @Inject
    ScriptExecutor scriptExecutor;

    @POST
    @Path("/{id}")
    public ExecResponse execute(@PathParam("id") String id, ExecRequest request) {
        try {
            // 查找脚本文件
            java.nio.file.Path scriptFile = findScriptFile(id);
            if (scriptFile == null) {
                return ExecResponse.error("脚本文件不存在: " + id);
            }

            // 读取脚本内容
            String scriptContent = Files.readString(scriptFile);
            if (scriptContent.trim().isEmpty()) {
                return ExecResponse.error("脚本内容为空");
            }

            // 根据文件扩展名判断脚本类型
            String scriptType = detectScriptType(scriptFile.getFileName().toString());

            // 执行脚本
            Object result = scriptExecutor.execute(scriptContent, scriptType, request);
            return ExecResponse.success(result);

        } catch (Exception e) {
            return ExecResponse.error(e.getMessage());
        }
    }

    private java.nio.file.Path findScriptFile(String id) {
        java.nio.file.Path scriptsDir = Paths.get("scripts");
        if (!Files.exists(scriptsDir)) {
            return null;
        }

        // 尝试各种扩展名
        String[] extensions = {".js", ".py", ".mjs"};
        for (String ext : extensions) {
            java.nio.file.Path file = scriptsDir.resolve(id + ext);
            if (Files.exists(file)) {
                return file;
            }
        }
        return null;
    }

    private String detectScriptType(String fileName) {
        if (fileName.endsWith(".py")) {
            return "python";
        } else if (fileName.endsWith(".js") || fileName.endsWith(".mjs")) {
            return "js";
        }
        // 默认使用 JavaScript
        return "js";
    }
}
