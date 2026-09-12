package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.jboss.logging.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件解压服务
 * <p>
 * 支持 zip 和 tar.gz 格式，解压到 /var/www/cus/ 目录下。
 * 若目标目录已存在则抛出异常，不覆盖。
 */
@ApplicationScoped
public class FileExtractorService {

    private static final Logger LOG = Logger.getLogger(FileExtractorService.class);

    /**
     * 解压根目录
     */
    private static final String EXTRACT_BASE_DIR = "/var/www/cus";

    /**
     * 解压上传的压缩文件
     *
     * @param fileName 文件名（用于判断压缩格式和提取顶层目录名）
     * @param fileData 文件字节数据
     * @return 解压后的目标目录路径
     * @throws IOException              IO 异常
     * @throws IllegalArgumentException 文件格式不支持或目标目录已存在
     */
    public Path extract(String fileName, byte[] fileData) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = fileName.toLowerCase();
        String appName = lowerName.substring(0,lowerName.indexOf("."));
        Path basePath = Paths.get(EXTRACT_BASE_DIR + File.separator + appName);
        // 确保基础目录存在
        if (!Files.exists(basePath)) {
            Files.createDirectories(basePath);
        }

        if (lowerName.endsWith(".zip")) {
            return extractZip(appName,fileData, basePath);
        } else if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")) {
            return extractTarGz(appName,fileData, basePath);
        } else {
            throw new IllegalArgumentException("不支持的文件格式，仅支持 .zip 和 .tar.gz");
        }
    }

    /**
     * 解压 ZIP 文件
     */
    private Path extractZip(String appName,byte[] fileData, Path basePath) throws IOException {
        // 先扫描获取顶层目录名
        String topDir = findTopLevelDirInZip(fileData);
        if (topDir == null) {
            throw new IllegalArgumentException("压缩包内没有顶层目录，无法确定解压位置");
        }

        Path targetDir = basePath.resolve(topDir);
        if (Files.exists(targetDir)) {
            throw new IllegalArgumentException("目录已存在，不覆盖: " + targetDir);
        }

        // 执行解压
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = resolveEntryPath(basePath, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        LOG.infof("ZIP 解压完成: %s", targetDir);
        return targetDir;
    }

    /**
     * 解压 tar.gz 文件
     * <p>
     * 要求压缩包内必须包含 qa 和 prd 目录，只解压 prd 目录下的文件到 basePath
     */
    private Path extractTarGz(String appName, byte[] fileData, Path basePath) throws IOException {
        // 如果 basePath 目录存在，先删除
        if (Files.exists(basePath)) {
            try {
                FileUtils.deleteDirectory(basePath.toFile());
            }catch(IOException ex){
                ex.printStackTrace();
            }
        }
        // 扫描获取顶层目录名（即 <appName>.tar），并验证 qa 和 prd 目录存在
        String topDir = findTopLevelDirInTarGz(fileData);
        if (topDir == null) {
            throw new IllegalArgumentException("压缩包内没有顶层目录，无法确定解压位置");
        }
        if(topDir.equals("prd")) {
            // 确保 basePath 目录存在
            Files.createDirectories(basePath);
            String prdPrefix = topDir + "/";
            String prdPrefixNoSlash = topDir + "";
            // 只解压 prd 目录下的文件到 basePath
            try (TarArchiveInputStream tis = new TarArchiveInputStream(
                    new GZIPInputStream(new ByteArrayInputStream(fileData)))) {
                TarArchiveEntry entry;
                while ((entry = tis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    // 去掉开头的 ./
                    if (entryName.startsWith("./")) {
                        entryName = entryName.substring(2);
                    }

                    // 只处理 prd 目录下的文件
                    if (!entryName.startsWith(prdPrefix) && !entryName.equals(prdPrefixNoSlash)) {
                        continue;
                    }

                    // prd 目录条目本身跳过（basePath 已创建）
                    if (entryName.equals(prdPrefixNoSlash)) {
                        continue;
                    }

                    // 去掉 topDir/prd/ 前缀，直接解压到 basePath
                    String relativePath = entryName.substring(prdPrefix.length());
                    if (relativePath.isEmpty()) {
                        continue;
                    }

                    Path entryPath = resolveEntryPath(basePath, relativePath);
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(tis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            LOG.infof("TAR.GZ 解压完成: %s", basePath);
            return basePath;
        }
        return null;
    }

    /**
     * 验证压缩包内是否包含 qa 和 prd 目录
     */
    private void validateQaAndPrdDirectories(byte[] fileData, String topDir) throws IOException {
        boolean hasQa = false;
        boolean hasPrd = false;
        String qaPrefix = topDir + "/qa";
        String prdPrefix = topDir + "/prd";

        try (TarArchiveInputStream tis = new TarArchiveInputStream(
                new GZIPInputStream(new ByteArrayInputStream(fileData)))) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                String name = entry.getName();
                // 去掉开头的 ./
                if (name.startsWith("./")) {
                    name = name.substring(2);
                }

                if (name.equals(qaPrefix) || name.equals(qaPrefix + "/") || name.startsWith(qaPrefix + "/")) {
                    hasQa = true;
                }
                if (name.equals(prdPrefix) || name.equals(prdPrefix + "/") || name.startsWith(prdPrefix + "/")) {
                    hasPrd = true;
                }

                if (hasQa && hasPrd) {
                    break;
                }
            }
        }

        if (!hasQa) {
            throw new IllegalArgumentException("压缩包内缺少 qa 目录: " + topDir + "/qa");
        }
        if (!hasPrd) {
            throw new IllegalArgumentException("压缩包内缺少 prd 目录: " + topDir + "/prd");
        }
    }

    /**
     * 扫描 ZIP 获取顶层目录名
     */
    private String findTopLevelDirInZip(byte[] fileData) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                String topDir = getTopLevelDir(name);
                if (topDir != null) {
                    return topDir;
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    /**
     * 扫描 tar.gz 获取顶层目录名
     */
    private String findTopLevelDirInTarGz(byte[] fileData) throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(
                new GZIPInputStream(new ByteArrayInputStream(fileData)))) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                String name = entry.getName();
                String topDir = getTopLevelDir(name);
                if (topDir != null) {
                    return topDir;
                }
            }
        }
        return null;
    }

    /**
     * 从条目路径中提取顶层目录名
     * <p>
     * 例如: "project/file.txt" → "project"
     * "project/" → "project"
     * "file.txt"（无目录）→ null
     */
    private String getTopLevelDir(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return null;
        }
        // 去掉开头的 ./
        String name = entryName;
        if (name.startsWith("./")) {
            name = name.substring(2);
        }
        // 去掉开头的 /
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return null;
        }

        int slashIdx = name.indexOf('/');
        if (slashIdx <= 0) {
            // 没有 "/" 或 "/" 在末尾，说明是根目录下的文件，不算顶层目录
            return null;
        }
        return name.substring(0, slashIdx);
    }

    /**
     * 安全解析条目路径，防止 zip-slip 攻击
     */
    private Path resolveEntryPath(Path basePath, String entryName) throws IOException {
        Path targetPath = basePath.resolve(entryName).normalize();
        if (!targetPath.startsWith(basePath)) {
            throw new IOException("非法的压缩条目路径: " + entryName);
        }
        return targetPath;
    }
}
