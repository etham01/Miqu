package com.miqu.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储。
 *
 * <p>抽象成接口是为了给将来接对象存储（OSS / MinIO）留位置：
 * 届时只需新增一个实现并切换 Bean，业务代码零改动。
 */
public interface FileStorageService {

    /**
     * 保存一张图片。
     *
     * @return 可直接用于 {@code <img src>} 的访问路径，如 {@code /uploads/image/2026/09/xxx.jpg}
     */
    String storeImage(MultipartFile file);
}
