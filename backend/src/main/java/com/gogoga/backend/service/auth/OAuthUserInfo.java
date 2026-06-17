package com.gogoga.backend.service.auth;

import lombok.Builder;
import lombok.Data;

/**
 * OAuth 用户信息封装类
 * 
 * 统一封装从各 OAuth 提供者获取的用户信息
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Data
@Builder
public class OAuthUserInfo {

    /**
     * 提供者侧用户唯一标识
     */
    private String providerUserId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像 URL
     */
    private String avatarUrl;

    /**
     * 显示名称（昵称）
     */
    private String displayName;

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 令牌过期时间（秒）
     */
    private Long expiresIn;
}
