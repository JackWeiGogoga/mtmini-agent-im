package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户第三方账号绑定实体类
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class UserAuth {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 认证提供者（GOOGLE/GITHUB/OPENID）
     */
    private String provider;

    /**
     * 提供者侧用户唯一标识
     */
    private String providerUserId;

    /**
     * 提供者侧用户名
     */
    private String providerUsername;

    /**
     * 提供者侧邮箱
     */
    private String providerEmail;

    /**
     * 提供者侧头像URL
     */
    private String providerAvatar;

    /**
     * OAuth访问令牌
     */
    private String accessToken;

    /**
     * OAuth刷新令牌
     */
    private String refreshToken;

    /**
     * 令牌过期时间
     */
    private LocalDateTime tokenExpireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
