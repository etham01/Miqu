package com.miqu.controller;

import com.miqu.common.Result;
import com.miqu.dto.request.LoginRequest;
import com.miqu.dto.request.RegisterRequest;
import com.miqu.security.LoginUser;
import com.miqu.service.AuthService;
import com.miqu.vo.LoginVO;
import com.miqu.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>注册与登录是免登录白名单；退出登录需要携带 Token（见 application.yml 的白名单配置）。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "注册、登录、退出")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "用户注册",
            description = "用户名与邮箱均需唯一。密码以 BCrypt 哈希存储，接口不会返回密码字段。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "注册成功"),
            @ApiResponse(responseCode = "400", description = "参数校验失败（用户名为空、密码长度不符、邮箱格式错误等）"),
            @ApiResponse(responseCode = "409", description = "用户名或邮箱已被占用")
    })
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录",
            description = """
                    成功返回 JWT，后续请求置于请求头：`Authorization: Bearer {token}`。

                    "用户不存在"与"密码错误"返回完全相同的 401，以防用户名枚举；
                    账号被禁用返回 423。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "400", description = "用户名或密码为空"),
            @ApiResponse(responseCode = "401", description = "用户名或密码错误"),
            @ApiResponse(responseCode = "423", description = "账号已被禁用")
    })
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录",
            description = """
                    本系统使用无状态 JWT，服务端不维护会话，因此退出登录由**客户端丢弃 Token** 完成。
                    该接口仅用于记录审计日志。

                    已知限制：已签发的 Token 在有效期内仍然可用。若需要服务端强制失效，
                    需引入 Redis 黑名单或改用短期 access token + refresh token。
                    """)
    public Result<Void> logout(LoginUser currentUser) {
        if (currentUser != null) {
            log.info("用户退出登录: userId={}", currentUser.userId());
        }
        return Result.ok();
    }
}
