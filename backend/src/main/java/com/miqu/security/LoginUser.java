package com.miqu.security;

import com.miqu.common.enums.RoleEnum;

/**
 * 当前登录用户的轻量上下文对象。
 *
 * <p>**不包含密码**，只保留鉴权与审计所需的最小字段。
 */
public record LoginUser(Long userId, String username, Integer role) {

    public boolean isAdmin() {
        return RoleEnum.isAdmin(role);
    }
}
