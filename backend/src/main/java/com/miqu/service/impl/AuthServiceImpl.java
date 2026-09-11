package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.enums.GenderEnum;
import com.miqu.common.enums.RoleEnum;
import com.miqu.common.enums.UserStatusEnum;
import com.miqu.converter.UserConverter;
import com.miqu.dto.request.LoginRequest;
import com.miqu.dto.request.RegisterRequest;
import com.miqu.entity.User;
import com.miqu.mapper.UserMapper;
import com.miqu.security.JwtTokenProvider;
import com.miqu.service.AuthService;
import com.miqu.vo.LoginVO;
import com.miqu.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // 快速失败：给出明确的"用户名已占用"，而不是等数据库抛约束冲突。
        // 注意这只是体验优化——并发下真正防重复的是 uk_username / uk_email 唯一键，
        // 冲突时由 GlobalExceptionHandler 翻译成同样的 409。
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
            throw BizException.of(ErrorCode.USERNAME_EXISTS);
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email))) {
            throw BizException.of(ErrorCode.EMAIL_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setEmail(email);
        user.setGender(request.gender() == null ? GenderEnum.UNKNOWN.getCode() : request.gender());
        user.setBirthday(request.birthday());
        user.setBio(blankToEmpty(request.bio()));
        user.setAvatar(blankToEmpty(request.avatar()));
        user.setRole(RoleEnum.USER.getCode());
        user.setStatus(UserStatusEnum.NORMAL.getCode());
        user.setFollowingCount(0);
        user.setFollowerCount(0);
        user.setPostCount(0);

        userMapper.insert(user);

        // 只记录可定位身份的非敏感字段，绝不打印密码或 Token
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return UserConverter.toVO(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        String username = request.username().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        // "用户不存在"与"密码错误"返回完全相同的响应，防止攻击者通过响应差异枚举有效用户名。
        // 先校验密码再看状态，这样被禁用账号在密码错误时也不会暴露"此账号存在且被禁用"。
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            log.info("登录失败: username={}, 原因=凭证不匹配", username);
            throw BizException.of(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!UserStatusEnum.isNormal(user.getStatus())) {
            log.info("登录失败: userId={}, 原因=账号已禁用", user.getId());
            throw BizException.of(ErrorCode.USER_DISABLED);
        }

        String token = jwtTokenProvider.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return new LoginVO(token, TOKEN_TYPE, jwtTokenProvider.getExpireSeconds(), UserConverter.toVO(user));
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
