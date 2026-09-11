package com.miqu.service;

import com.miqu.dto.request.LoginRequest;
import com.miqu.dto.request.RegisterRequest;
import com.miqu.vo.LoginVO;
import com.miqu.vo.UserVO;

public interface AuthService {

    /** 注册新用户，返回创建后的用户信息。 */
    UserVO register(RegisterRequest request);

    /** 登录，成功返回 Token 与用户信息。 */
    LoginVO login(LoginRequest request);
}
