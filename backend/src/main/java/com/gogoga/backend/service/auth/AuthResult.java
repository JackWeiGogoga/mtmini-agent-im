package com.gogoga.backend.service.auth;

import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.AuthProviderEnum;

import lombok.Builder;
import lombok.Data;

/**
 * 认证结果封装类
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Data
@Builder
public class AuthResult {

    /**
     * 是否认证成功
     */
    private boolean success;

    /**
     * 认证成功后的用户实体
     */
    private User user;

    /**
     * 会话 Token
     */
    private String sessionToken;

    /**
     * 会话过期时间（秒）
     */
    private Long sessionExpireSeconds;

    /**
     * 认证提供者类型
     */
    private AuthProviderEnum providerType;

    /**
     * 是否为新用户（首次登录）
     */
    private boolean newUser;

    /**
     * 错误码（认证失败时）
     */
    private String errorCode;

    /**
     * 错误信息（认证失败时）
     */
    private String errorMessage;

    /**
     * 创建成功结果
     */
    public static AuthResult success(User user, String sessionToken, Long expireSeconds, 
                                      AuthProviderEnum providerType, boolean isNewUser) {
        return AuthResult.builder()
                .success(true)
                .user(user)
                .sessionToken(sessionToken)
                .sessionExpireSeconds(expireSeconds)
                .providerType(providerType)
                .newUser(isNewUser)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static AuthResult failure(String errorCode, String errorMessage) {
        return AuthResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
