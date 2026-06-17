package com.gogoga.backend.enums;

/**
 * 认证提供者枚举
 *
 * @author system
 * @since 2025-01-21
 */
public enum AuthProviderEnum {

    /**
     * 企业 OpenID 登录
     */
    OPENID("OPENID", "企业OpenID"),

    /**
     * 邮箱密码登录
     */
    PASSWORD("PASSWORD", "邮箱密码"),

    /**
     * 手机验证码登录
     */
    SMS("SMS", "短信验证码"),

    /**
     * Google OAuth 登录
     */
    GOOGLE("GOOGLE", "Google"),

    /**
     * GitHub OAuth 登录
     */
    GITHUB("GITHUB", "GitHub"),

    /**
     * 美团 H5 门店登录
     */
    MT_H5("MT_H5", "美团H5门店");

    private final String code;
    private final String description;

    AuthProviderEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 获取枚举值
     *
     * @param code 认证提供者代码
     * @return 对应的枚举值，如果不存在则返回 null
     */
    public static AuthProviderEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AuthProviderEnum provider : values()) {
            if (provider.getCode().equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return null;
    }
}
