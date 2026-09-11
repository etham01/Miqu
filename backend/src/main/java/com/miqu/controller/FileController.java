package com.miqu.controller;

import com.miqu.common.Result;
import com.miqu.service.FileStorageService;
import com.miqu.vo.FileUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传。
 *
 * <p>只负责"存文件 + 返回 URL"。头像与动态图片都复用此接口，
 * 业务接口只接收 URL，上传与业务解耦，两边都好测。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "文件", description = "图片上传")
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传图片",
            description = """
                    表单字段名为 `file`，大小上限 5MB。

                    服务端按**文件头魔数**判断真实类型（支持 jpg / png / gif / webp），
                    仅改后缀名的伪装文件会被拒绝；存储时用 UUID 重命名，不使用原始文件名。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功，返回可访问的 URL"),
            @ApiResponse(responseCode = "400", description = "文件为空、超过 5MB，或不是受支持的图片格式"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<FileUploadVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.ok(new FileUploadVO(fileStorageService.storeImage(file)));
    }
}
