package com.gogoga.backend.service.auth;

import com.gogoga.backend.enums.AuthProviderEnum;

import lombok.Builder;
import lombok.Data;

/**
 * 认证请求封装类
 * 
 * 用于统一封装不同认证方式的请求参数
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Data
@Builder
public class AuthRequest {

    /**
     * 认证提供者类型
     */
    private AuthProviderEnum providerType;

    /**
     * 邮箱（用于邮箱密码登录）
     */
    private String email;

    /**
     * 密码（用于邮箱密码登录）
     */
    private String password;

    /**
     * 手机号（用于短信验证码登录）
     */
    private String phone;

    /**
     * 短信验证码（用于短信验证码登录）
     */
    private String smsCode;

    /**
     * 是否记住登录状态（扩展会话到30天）
     */
    private Boolean rememberMe;

    /**
     * OAuth 授权码（用于 OAuth 登录）
     */
    private String code;

    /**
     * OAuth 状态参数（用于 CSRF 防护）
     */
    private String state;

    /**
     * OAuth 重定向 URI
     */
    private String redirectUri;

    /**
     * 客户端 IP 地址（用于日志和安全）
     */
    private String clientIp;

    /**
     * 用户代理（用于日志和安全）
     */
    private String userAgent;

    /**
     * 指定后台工作台
     */
    private String portal;
}
