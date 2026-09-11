package com.miqu.service.impl;

import com.miqu.common.BizException;
import com.miqu.common.BizConstants;
import com.miqu.common.ErrorCode;
import com.miqu.config.MiquProperties;
import com.miqu.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘文件存储。
 *
 * <p>安全要点（缺一不可）：
 * <ol>
 *   <li><b>按文件头魔数判断类型</b>，而不是看后缀名——把脚本改名为 .jpg 就能绕过后缀校验</li>
 *   <li><b>用 UUID 重命名</b>，不使用用户提供的原始文件名，杜绝 {@code ../../} 路径穿越</li>
 *   <li>按年月分目录，避免单目录文件过多，也便于将来整体迁移到对象存储</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final String SUB_DIR = "image";

    private final MiquProperties properties;

    @Override
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > BizConstants.MAX_IMAGE_SIZE) {
            throw BizException.of(ErrorCode.FILE_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw BizException.of(ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }

        String extension = detectImageExtension(bytes);
        if (extension == null) {
            throw BizException.of(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        String relativeDir = SUB_DIR + "/" + LocalDate.now().format(YEAR_MONTH);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path baseDir = Paths.get(properties.getUpload().getBaseDir()).toAbsolutePath().normalize();
        Path targetDir = baseDir.resolve(relativeDir).normalize();

        // 双保险：即使前面的命名逻辑被改坏，也不允许写出根目录之外
        if (!targetDir.startsWith(baseDir)) {
            throw BizException.of(ErrorCode.INTERNAL_ERROR, "非法的存储路径");
        }

        try {
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(filename), bytes);
        } catch (IOException e) {
            log.error("保存上传文件失败: dir={}, filename={}", targetDir, filename, e);
            throw BizException.of(ErrorCode.INTERNAL_ERROR, "保存文件失败");
        }

        String url = properties.getUpload().getUrlPrefix() + "/" + relativeDir + "/" + filename;
        log.info("图片上传成功: url={}, size={} bytes", url, bytes.length);
        return url;
    }

    /**
     * 通过文件头魔数识别真实图片类型。
     *
     * @return 小写扩展名；不是受支持的图片则返回 {@code null}
     */
    private String detectImageExtension(byte[] b) {
        if (b.length < 12) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "png";
        }
        // GIF: "GIF8"
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "gif";
        }
        // WebP: "RIFF" .... "WEBP"
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
