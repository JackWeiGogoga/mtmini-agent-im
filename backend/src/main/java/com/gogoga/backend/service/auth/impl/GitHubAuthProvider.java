package com.gogoga.backend.service.auth.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.config.OAuthProperties;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.entity.UserAuth;
import com.gogoga.backend.entity.UserRole;
import com.gogoga.backend.enums.AuthProviderEnum;
import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.mapper.UserAuthMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.service.auth.AuthProvider;
import com.gogoga.backend.service.auth.AuthRequest;
import com.gogoga.backend.service.auth.OAuthUserInfo;
import com.gogoga.backend.util.JsonUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * GitHub OAuth 认证提供者
 * 
 * 处理 GitHub OAuth 2.0 登录流程
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubAuthProvider implements AuthProvider {

    /**
     * 默认角色ID（普通用户）
     */
    private static final String DEFAULT_ROLE_ID = "ROLE_USER";

    private final OAuthProperties oAuthProperties;
    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final UserRoleMapper userRoleMapper;
    private final OkHttpClient httpClient;

    @Override
    public AuthProviderEnum getProviderType() {
        return AuthProviderEnum.GITHUB;
    }

    @Override
    public User authenticate(AuthRequest authRequest) {
        String code = authRequest.getCode();
        String redirectUri = authRequest.getRedirectUri();

        if (StringUtils.isBlank(code)) {
            throw new AuthenticationException(
                    AuthenticationException.OAUTH_ERROR,
                    "GitHub 授权码不能为空");
        }

        // 1. 用授权码换取 access_token 和用户信息
        OAuthUserInfo userInfo = exchangeCodeForUserInfo(code, redirectUri);

        // 2. 检查是否已绑定用户
        UserAuth existingAuth = userAuthMapper.selectByProviderAndProviderUserId(
                AuthProviderEnum.GITHUB.name(), userInfo.getProviderUserId());

        User user;
        if (existingAuth != null) {
            // 已绑定，获取用户并更新 token
            user = userMapper.selectByUserId(existingAuth.getUserId());
            if (user == null) {
                throw new AuthenticationException(
                        AuthenticationException.USER_NOT_FOUND,
                        "关联的用户不存在");
            }
            // 更新 token 信息
            updateAuthToken(existingAuth, userInfo);
        } else {
            // 未绑定，创建新用户（直接登录模式）
            user = createNewUserFromOAuth(userInfo);
        }

        // 检查用户状态
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new AuthenticationException(
                    AuthenticationException.USER_DISABLED,
                    "用户账号已被禁用");
        }

        log.info("GitHub OAuth 登录成功, userId={}, githubUserId={}", 
                user.getUserId(), userInfo.getProviderUserId());
        return user;
    }

    @Override
    public boolean supports(AuthRequest authRequest) {
        return authRequest.getProviderType() == AuthProviderEnum.GITHUB
                && StringUtils.isNotBlank(authRequest.getCode());
    }

    /**
     * 用授权码换取用户信息
     */
    private OAuthUserInfo exchangeCodeForUserInfo(String code, String redirectUri) {
        OAuthProperties.GitHubConfig config = oAuthProperties.getGithub();
        String finalRedirectUri = StringUtils.isNotBlank(redirectUri) 
                ? redirectUri : config.getRedirectUri();

        // 1. 换取 access_token
        RequestBody tokenRequestBody = new FormBody.Builder()
                .add("client_id", config.getClientId())
                .add("client_secret", config.getClientSecret())
                .add("code", code)
                .add("redirect_uri", finalRedirectUri)
                .build();

        Request tokenRequest = new Request.Builder()
                .url(config.getTokenUri())
                .header("Accept", "application/json")
                .post(tokenRequestBody)
                .build();

        String accessToken;

        try (Response response = httpClient.newCall(tokenRequest).execute()) {
            if (!response.isSuccessful()) {
                log.error("GitHub token 请求失败, code={}, status={}", code, response.code());
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "获取 GitHub 访问令牌失败");
            }

            JsonNode tokenJson = JsonUtil.readTree(response.body().string());
            if (tokenJson == null) {
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "解析 GitHub 响应失败");
            }
            if (tokenJson.has("error")) {
                String error = tokenJson.get("error").asText();
                String errorDesc = tokenJson.has("error_description") 
                        ? tokenJson.get("error_description").asText() : error;
                log.error("GitHub token 请求失败: {}", errorDesc);
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "获取 GitHub 访问令牌失败: " + errorDesc);
            }
            accessToken = tokenJson.get("access_token").asText();
        } catch (IOException e) {
            log.error("GitHub token 请求异常", e);
            throw new AuthenticationException(
                    AuthenticationException.OAUTH_ERROR,
                    "获取 GitHub 访问令牌失败: " + e.getMessage());
        }

        // 2. 获取用户信息
        Request userInfoRequest = new Request.Builder()
                .url(config.getUserInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .get()
                .build();

        String providerUserId;
        String username;
        String displayName;
        String avatarUrl;

        try (Response response = httpClient.newCall(userInfoRequest).execute()) {
            if (!response.isSuccessful()) {
                log.error("GitHub 用户信息请求失败, status={}", response.code());
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "获取 GitHub 用户信息失败");
            }

            JsonNode userJson = JsonUtil.readTree(response.body().string());
            if (userJson == null) {
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "解析 GitHub 用户信息失败");
            }
            providerUserId = String.valueOf(userJson.get("id").asLong());
            username = userJson.has("login") ? userJson.get("login").asText() : null;
            displayName = userJson.has("name") && !userJson.get("name").isNull() 
                    ? userJson.get("name").asText() : username;
            avatarUrl = userJson.has("avatar_url") ? userJson.get("avatar_url").asText() : null;
        } catch (IOException e) {
            log.error("GitHub 用户信息请求异常", e);
            throw new AuthenticationException(
                    AuthenticationException.OAUTH_ERROR,
                    "获取 GitHub 用户信息失败: " + e.getMessage());
        }

        // 3. 获取用户邮箱（GitHub 需要单独请求）
        String email = fetchUserEmail(accessToken, config);

        return OAuthUserInfo.builder()
                .providerUserId(providerUserId)
                .email(email)
                .username(username)
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .accessToken(accessToken)
                .build();
    }

    /**
     * 获取 GitHub 用户邮箱
     */
    private String fetchUserEmail(String accessToken, OAuthProperties.GitHubConfig config) {
        Request emailRequest = new Request.Builder()
                .url(config.getUserEmailUri())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(emailRequest).execute()) {
            if (!response.isSuccessful()) {
                log.warn("GitHub 邮箱请求失败, status={}", response.code());
                return null;
            }

            JsonNode emailsJson = JsonUtil.readTree(response.body().string());
            if (emailsJson == null) {
                return null;
            }
            // 优先获取 primary 且 verified 的邮箱
            for (JsonNode emailNode : emailsJson) {
                boolean primary = emailNode.has("primary") && emailNode.get("primary").asBoolean();
                boolean verified = emailNode.has("verified") && emailNode.get("verified").asBoolean();
                if (primary && verified) {
                    return emailNode.get("email").asText();
                }
            }
            // 没有 primary，返回第一个 verified 的邮箱
            for (JsonNode emailNode : emailsJson) {
                boolean verified = emailNode.has("verified") && emailNode.get("verified").asBoolean();
                if (verified) {
                    return emailNode.get("email").asText();
                }
            }
            return null;
        } catch (IOException e) {
            log.warn("GitHub 邮箱请求异常", e);
            return null;
        }
    }

    /**
     * 从 OAuth 信息创建新用户（直接登录模式）
     */
    private User createNewUserFromOAuth(OAuthUserInfo userInfo) {
        // 创建用户
        User user = new User();
        user.setUserId(UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(userInfo.getUsername());
        user.setNickname(userInfo.getDisplayName());
        user.setEmail(userInfo.getEmail());
        user.setAvatar(userInfo.getAvatarUrl());
        user.setAuthProvider(AuthProviderEnum.GITHUB.name());
        user.setStatus(1);

        userMapper.insert(user);

        // 创建绑定记录
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(user.getUserId());
        userAuth.setProvider(AuthProviderEnum.GITHUB.name());
        userAuth.setProviderUserId(userInfo.getProviderUserId());
        userAuth.setProviderUsername(userInfo.getUsername());
        userAuth.setProviderEmail(userInfo.getEmail());
        userAuth.setProviderAvatar(userInfo.getAvatarUrl());
        userAuth.setAccessToken(userInfo.getAccessToken());

        userAuthMapper.insert(userAuth);

        // 为新用户分配默认角色（普通用户）
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insert(userRole);

        log.info("通过 GitHub OAuth 创建新用户, userId={}, githubUserId={}, 已分配默认角色: {}", 
                user.getUserId(), userInfo.getProviderUserId(), DEFAULT_ROLE_ID);
        return user;
    }

    /**
     * 更新已有绑定的 token 信息
     */
    private void updateAuthToken(UserAuth existingAuth, OAuthUserInfo userInfo) {
        // 目前简化处理，后续可以添加更新逻辑
        log.debug("更新 GitHub OAuth token, userId={}", existingAuth.getUserId());
    }
}
