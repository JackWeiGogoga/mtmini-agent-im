package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OAuth 配置属性
 *
 * @author system
 * @since 2025-01-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    /**
     * Google OAuth 配置
     */
    private GoogleConfig google = new GoogleConfig();

    /**
     * GitHub OAuth 配置
     */
    private GitHubConfig github = new GitHubConfig();

    /**
     * Google OAuth 配置
     */
    @Data
    public static class GoogleConfig {

        /**
         * OAuth Client ID
         */
        private String clientId;

        /**
         * OAuth Client Secret
         */
        private String clientSecret;

        /**
         * 重定向 URI
         */
        private String redirectUri;

        /**
         * 授权端点 URI
         */
        private String authorizationUri = "https://accounts.google.com/o/oauth2/v2/auth";

        /**
         * Token 端点 URI
         */
        private String tokenUri = "https://oauth2.googleapis.com/token";

        /**
         * 用户信息端点 URI
         */
        private String userInfoUri = "https://www.googleapis.com/oauth2/v3/userinfo";

        /**
         * 授权范围
         */
        private String scope = "openid email profile";
    }

    /**
     * GitHub OAuth 配置
     */
    @Data
    public static class GitHubConfig {

        /**
         * OAuth Client ID
         */
        private String clientId;

        /**
         * OAuth Client Secret
         */
        private String clientSecret;

        /**
         * 重定向 URI
         */
        private String redirectUri;

        /**
         * 授权端点 URI
         */
        private String authorizationUri = "https://github.com/login/oauth/authorize";

        /**
         * Token 端点 URI
         */
        private String tokenUri = "https://github.com/login/oauth/access_token";

        /**
         * 用户信息端点 URI
         */
        private String userInfoUri = "https://api.github.com/user";

        /**
         * 用户邮箱端点 URI
         */
        private String userEmailUri = "https://api.github.com/user/emails";

        /**
         * 授权范围
         */
        private String scope = "read:user user:email";
    }
}
