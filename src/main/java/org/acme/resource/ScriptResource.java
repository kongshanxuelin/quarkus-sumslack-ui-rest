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

import java.io.File;
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
        return listInternal(null);
    }

    @GET
    @Path("/list/{path: .*}")
    public ExecResponse listByPath(@PathParam("path") String path) {
        return listInternal(path);
    }

    private ExecResponse listInternal(String subPath) {
        try {
            java.nio.file.Path scriptsDir = Paths.get(SCRIPTS_DIR);
            if (subPath != null && !subPath.isEmpty()) {
                scriptsDir = scriptsDir.resolve(subPath);
            }
            if (!Files.exists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
                return ExecResponse.success(new ArrayList<>());
            }

            List<Map<String, Object>> result = buildTree(scriptsDir);
            return ExecResponse.success(result);
        } catch (IOException e) {
            return ExecResponse.error("列出脚本失败: " + e.getMessage());
        }
    }

    private String formatNodeId(String id){
//        return id.replaceAll("\\/","_");
        return id;
    }

    private List<Map<String, Object>> buildTree(java.nio.file.Path dir) throws IOException {
        List<Map<String, Object>> items = new ArrayList<>();

        // 用于存储目录及其子项
        Map<String, Map<String, Object>> dirMap = new LinkedHashMap<>();

        try (Stream<java.nio.file.Path> paths = Files.list(dir)) {
            List<java.nio.file.Path> pathList = paths.sorted().toList();

            for (java.nio.file.Path p : pathList) {
                String name = p.getFileName().toString();

                if (Files.isDirectory(p)) {
                    // 目录
                    String relativePath = getRelativePath(Paths.get(SCRIPTS_DIR), p);
                    Map<String, Object> dirInfo = new LinkedHashMap<>();
                    dirInfo.put("id", formatNodeId(relativePath));
                    dirInfo.put("name", name);
                    dirInfo.put("type", "directory");
                    dirInfo.put("isLeaf",false);
                    dirInfo.put("children", buildTree(p));
                    items.add(dirInfo);
                } else if (isScriptFile(name)) {
                    // 脚本文件
                    String relativePath = getRelativePath(Paths.get(SCRIPTS_DIR), p);
                    String id = getNameWithoutExtension(relativePath);
                    Map<String, Object> fileInfo = new LinkedHashMap<>();
                    fileInfo.put("id", formatNodeId(id));
                    fileInfo.put("name", name);
                    fileInfo.put("type", "script");
                    fileInfo.put("isLeaf",true);
                    fileInfo.put("scriptType", getScriptType(name));
                    fileInfo.put("size", getFileSize(p));
                    fileInfo.put("lastModified", getLastModified(p));
                    items.add(fileInfo);
                }
            }
        }

        return items;
    }

    private boolean isScriptFile(String name) {
        return name.endsWith(".js") || name.endsWith(".py") || name.endsWith(".mjs");
    }

    @GET
    @Path("/{id: .*}")
    public ExecResponse get(@PathParam("id") String id) {
        try {
            java.nio.file.Path scriptFile = findScriptFile(id);
            if (scriptFile == null) {
                return ExecResponse.error("脚本不存在: " + id);
            }

            String relativePath = getRelativePath(Paths.get(SCRIPTS_DIR), scriptFile);
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            result.put("fileName", scriptFile.getFileName().toString());
            result.put("path", relativePath);
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
        String path = request.get("path");
        if (id == null || id.trim().isEmpty()) {
            return ExecResponse.error("脚本ID不能为空");
        }
        if (content == null) {
            content = getDefaultContent(type);
        }

        try {
            java.nio.file.Path scriptsDir = Paths.get(SCRIPTS_DIR + File.separator + path);
            String extension = getExtension(type);
            java.nio.file.Path scriptFile = scriptsDir.resolve(id + extension);

            if (Files.exists(scriptFile)) {
                return ExecResponse.error("脚本已存在: " + id);
            }

            // 确保父目录存在
            java.nio.file.Path parentDir = scriptFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(scriptFile, content);
            return ExecResponse.success(Map.of("id", id, "fileName", scriptFile.getFileName().toString()));
        } catch (IOException e) {
            return ExecResponse.error("创建脚本失败: " + e.getMessage());
        }
    }

    @POST
    @Path("/mkdir")
    public ExecResponse mkdir(Map<String, String> request) {
        String name = request.get("name");
        String path = request.get("path");

        if (name == null || name.trim().isEmpty()) {
            return ExecResponse.error("目录名不能为空");
        }

        // 不允许目录名包含路径分隔符
        if (name.contains("/") || name.contains("\\")) {
            return ExecResponse.error("目录名不能包含路径分隔符");
        }

        try {
            java.nio.file.Path scriptsDir = Paths.get(SCRIPTS_DIR);
            java.nio.file.Path targetDir;

            if (path != null && !path.trim().isEmpty()) {
                targetDir = scriptsDir.resolve(path).resolve(name);
            } else {
                targetDir = scriptsDir.resolve(name);
            }

            // 检查是否已存在
            if (Files.exists(targetDir)) {
                return ExecResponse.error("目录已存在: " + name);
            }

            Files.createDirectories(targetDir);

            String relativePath = getRelativePath(scriptsDir, targetDir);
            return ExecResponse.success(Map.of(
                "id", relativePath,
                "name", name,
                "type", "directory",
                "isLeaf", false
            ));
        } catch (IOException e) {
            return ExecResponse.error("创建目录失败: " + e.getMessage());
        }
    }

    @PUT
    @Path("/{id: .*}")
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
    @Path("/{id: .*}")
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

    private String getDefaultContent(String type) {
        if ("js".equals(type)) {
            return """
(function(){
    //TODO:params可以获取网页参数
})()
""";
        } else if ("py".equals(type)) {
            return """
def ok(params):
    #TODO:params可以获取网页参数
    return "hello"
ok(params)
""";
        }
        return "";
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

    private String getRelativePath(java.nio.file.Path base, java.nio.file.Path file) {
        return base.relativize(file).toString().replace("\\", "/");
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
