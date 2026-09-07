package org.acme.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.dto.ExecResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Path("/node/api/scripts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScriptResource {

    private static final String SCRIPTS_DIR = "scripts";

    @GET
    @Path("/list")
    public ExecResponse list() {
        try {
            java.nio.file.Path scriptsDir = Paths.get(SCRIPTS_DIR);
            if (!Files.exists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
                return ExecResponse.success(new ArrayList<>());
            }

            List<Map<String, Object>> scripts = new ArrayList<>();
            try (Stream<java.nio.file.Path> paths = Files.list(scriptsDir)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> {
                         String name = p.getFileName().toString();
                         return name.endsWith(".js") || name.endsWith(".py") || name.endsWith(".mjs");
                     })
                     .forEach(p -> {
                         Map<String, Object> info = new HashMap<>();
                         String fileName = p.getFileName().toString();
                         info.put("id", getNameWithoutExtension(fileName));
                         info.put("fileName", fileName);
                         info.put("type", getScriptType(fileName));
                         info.put("size", getFileSize(p));
                         info.put("lastModified", getLastModified(p));
                         scripts.add(info);
                     });
            }
            return ExecResponse.success(scripts);
        } catch (IOException e) {
            return ExecResponse.error("列出脚本失败: " + e.getMessage());
        }
    }

    @GET
    @Path("/{id}")
    public ExecResponse get(@PathParam("id") String id) {
        try {
            java.nio.file.Path scriptFile = findScriptFile(id);
            if (scriptFile == null) {
                return ExecResponse.error("脚本不存在: " + id);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("fileName", scriptFile.getFileName().toString());
            result.put("type", getScriptType(scriptFile.getFileName().toString()));
            result.put("content", Files.readString(scriptFile));
            result.put("size", getFileSize(scriptFile));
            result.put("lastModified", getLastModified(scriptFile));

            return ExecResponse.success(result);
        } catch (IOException e) {
            return ExecResponse.error("读取脚本失败: " + e.getMessage());
        }
    }

    @POST
    @Path("/create")
    public ExecResponse create(Map<String, String> request) {
        String id = request.get("id");
        String content = request.get("content");
        String type = request.getOrDefault("type", "js");

        if (id == null || id.trim().isEmpty()) {
            return ExecResponse.error("脚本ID不能为空");
        }
        if (content == null) {
            return ExecResponse.error("脚本内容不能为空");
        }

        try {
            java.nio.file.Path scriptsDir = Paths.get(SCRIPTS_DIR);
            if (!Files.exists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
            }

            String extension = getExtension(type);
            java.nio.file.Path scriptFile = scriptsDir.resolve(id + extension);

            if (Files.exists(scriptFile)) {
                return ExecResponse.error("脚本已存在: " + id);
            }
            if(type.equals("js")){
                content = """
(function(){
    //TODO:params可以获取网页参数
})()
""";
            }else if(type.equals("py")){
                content = """
def ok(params):
    //TODO:params可以获取网页参数
    return "hello"
ok(params)
""";
            }
            Files.writeString(scriptFile, content);
            return ExecResponse.success(Map.of("id", id, "fileName", scriptFile.getFileName().toString()));
        } catch (IOException e) {
            return ExecResponse.error("创建脚本失败: " + e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    public ExecResponse update(@PathParam("id") String id, Map<String, String> request) {
        String content = request.get("content");
        String type = request.get("type");

        if (content == null && type == null) {
            return ExecResponse.error("请提供要更新的内容或类型");
        }

        try {
            java.nio.file.Path scriptFile = findScriptFile(id);
            if (scriptFile == null) {
                return ExecResponse.error("脚本不存在: " + id);
            }

            // 如果提供了新类型，需要重命名文件
            if (type != null && !type.isEmpty()) {
                String newExtension = getExtension(type);
                String oldFileName = scriptFile.getFileName().toString();
                String oldExtension = getExtensionFromFileName(oldFileName);

                if (!newExtension.equals(oldExtension)) {
                    java.nio.file.Path newFile = scriptFile.getParent().resolve(id + newExtension);
                    if (Files.exists(newFile)) {
                        return ExecResponse.error("目标文件已存在: " + id + newExtension);
                    }
                    Files.move(scriptFile, newFile);
                    scriptFile = newFile;
                }
            }

            // 更新内容
            if (content != null) {
                Files.writeString(scriptFile, content);
            }

            return ExecResponse.success(Map.of("id", id, "fileName", scriptFile.getFileName().toString()));
        } catch (IOException e) {
            return ExecResponse.error("更新脚本失败: " + e.getMessage());
        }
    }

    @DELETE
    @Path("/{id}")
    public ExecResponse delete(@PathParam("id") String id) {
        try {
            java.nio.file.Path scriptFile = findScriptFile(id);
            if (scriptFile == null) {
                return ExecResponse.error("脚本不存在: " + id);
            }

            Files.delete(scriptFile);
            return ExecResponse.success(Map.of("id", id, "deleted", true));
        } catch (IOException e) {
            return ExecResponse.error("删除脚本失败: " + e.getMessage());
        }
    }

    private java.nio.file.Path findScriptFile(String id) {
        java.nio.file.Path scriptsDir = Paths.get(SCRIPTS_DIR);
        if (!Files.exists(scriptsDir)) {
            return null;
        }

        String[] extensions = {".js", ".py", ".mjs"};
        for (String ext : extensions) {
            java.nio.file.Path file = scriptsDir.resolve(id + ext);
            if (Files.exists(file)) {
                return file;
            }
        }
        return null;
    }

    private String getNameWithoutExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private String getExtensionFromFileName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }

    private String getScriptType(String fileName) {
        if (fileName.endsWith(".py")) {
            return "python";
        } else if (fileName.endsWith(".js") || fileName.endsWith(".mjs")) {
            return "js";
        }
        return "unknown";
    }

    private String getExtension(String type) {
        if ("python".equalsIgnoreCase(type) || "py".equalsIgnoreCase(type)) {
            return ".py";
        }
        return ".js";
    }

    private long getFileSize(java.nio.file.Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private String getLastModified(java.nio.file.Path path) {
        try {
            long millis = Files.getLastModifiedTime(path).toMillis();
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(millis));
        } catch (IOException e) {
            return "";
        }
    }
}
