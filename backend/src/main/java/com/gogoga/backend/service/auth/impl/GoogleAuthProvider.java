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
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Google OAuth 认证提供者
 * 
 * 处理 Google OAuth 2.0 登录流程
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleAuthProvider implements AuthProvider {

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
        return AuthProviderEnum.GOOGLE;
    }

    @Override
    public User authenticate(AuthRequest authRequest) {
        String code = authRequest.getCode();
        String redirectUri = authRequest.getRedirectUri();

        if (StringUtils.isBlank(code)) {
            throw new AuthenticationException(
                    AuthenticationException.OAUTH_ERROR,
                    "Google 授权码不能为空");
        }

        // 1. 用授权码换取 access_token
        OAuthUserInfo userInfo = exchangeCodeForUserInfo(code, redirectUri);

        // 2. 检查是否已绑定用户
        UserAuth existingAuth = userAuthMapper.selectByProviderAndProviderUserId(
                AuthProviderEnum.GOOGLE.name(), userInfo.getProviderUserId());

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

        log.info("Google OAuth 登录成功, userId={}, googleUserId={}", 
                user.getUserId(), userInfo.getProviderUserId());
        return user;
    }

    @Override
    public boolean supports(AuthRequest authRequest) {
        return authRequest.getProviderType() == AuthProviderEnum.GOOGLE
                && StringUtils.isNotBlank(authRequest.getCode());
    }

    /**
     * 用授权码换取用户信息
     */
    private OAuthUserInfo exchangeCodeForUserInfo(String code, String redirectUri) {
        OAuthProperties.GoogleConfig config = oAuthProperties.getGoogle();
        String finalRedirectUri = StringUtils.isNotBlank(redirectUri) 
                ? redirectUri : config.getRedirectUri();

        // 1. 换取 access_token
        RequestBody tokenRequestBody = new FormBody.Builder()
                .add("client_id", config.getClientId())
                .add("client_secret", config.getClientSecret())
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", finalRedirectUri)
                .build();

        Request tokenRequest = new Request.Builder()
                .url(config.getTokenUri())
                .post(tokenRequestBody)
                .build();

        String accessToken;
        String refreshToken = null;
        Long expiresIn = null;

        try (Response response = httpClient.newCall(tokenRequest).execute()) {
            if (!response.isSuccessful()) {
                log.error("Google token 请求失败, code={}, status={}", code, response.code());
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "获取 Google 访问令牌失败");
            }

            JsonNode tokenJson = JsonUtil.readTree(response.body().string());
            if (tokenJson == null) {
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "解析 Google 响应失败");
            }
            accessToken = tokenJson.get("access_token").asText();
            if (tokenJson.has("refresh_token")) {
                refreshToken = tokenJson.get("refresh_token").asText();
            }
            if (tokenJson.has("expires_in")) {
                expiresIn = tokenJson.get("expires_in").asLong();
            }
        } catch (IOException e) {
            log.error("Google token 请求异常", e);
            throw new AuthenticationException(
                    AuthenticationException.OAUTH_ERROR,
                    "获取 Google 访问令牌失败: " + e.getMessage());
        }

        // 2. 获取用户信息
        Request userInfoRequest = new Request.Builder()
                .url(config.getUserInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(userInfoRequest).execute()) {
            if (!response.isSuccessful()) {
                log.error("Google 用户信息请求失败, status={}", response.code());
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "获取 Google 用户信息失败");
            }

            JsonNode userJson = JsonUtil.readTree(response.body().string());
            if (userJson == null) {
                throw new AuthenticationException(
                        AuthenticationException.OAUTH_ERROR,
                        "解析 Google 用户信息失败");
            }
            return OAuthUserInfo.builder()
                    .providerUserId(userJson.get("sub").asText())
                    .email(userJson.has("email") ? userJson.get("email").asText() : null)
                    .username(userJson.has("name") ? userJson.get("name").asText() : null)
                    .displayName(userJson.has("name") ? userJson.get("name").asText() : null)
                    .avatarUrl(userJson.has("picture") ? userJson.get("picture").asText() : null)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(expiresIn)
                    .build();
        } catch (IOException e) {
            log.error("Google 用户信息请求异常", e);
            throw new AuthenticationException(
                    AuthenticationException.OAUTH_ERROR,
                    "获取 Google 用户信息失败: " + e.getMessage());
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
        user.setAuthProvider(AuthProviderEnum.GOOGLE.name());
        user.setStatus(1);

        userMapper.insert(user);

        // 创建绑定记录
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(user.getUserId());
        userAuth.setProvider(AuthProviderEnum.GOOGLE.name());
        userAuth.setProviderUserId(userInfo.getProviderUserId());
        userAuth.setProviderUsername(userInfo.getUsername());
        userAuth.setProviderEmail(userInfo.getEmail());
        userAuth.setProviderAvatar(userInfo.getAvatarUrl());
        userAuth.setAccessToken(userInfo.getAccessToken());
        userAuth.setRefreshToken(userInfo.getRefreshToken());
        if (userInfo.getExpiresIn() != null) {
            userAuth.setTokenExpireTime(LocalDateTime.now().plusSeconds(userInfo.getExpiresIn()));
        }

        userAuthMapper.insert(userAuth);

        // 为新用户分配默认角色（普通用户）
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insert(userRole);

        log.info("通过 Google OAuth 创建新用户, userId={}, googleUserId={}, 已分配默认角色: {}", 
                user.getUserId(), userInfo.getProviderUserId(), DEFAULT_ROLE_ID);
        return user;
    }

    /**
     * 更新已有绑定的 token 信息
     */
    private void updateAuthToken(UserAuth existingAuth, OAuthUserInfo userInfo) {
        // 目前简化处理，后续可以添加更新逻辑
        log.debug("更新 Google OAuth token, userId={}", existingAuth.getUserId());
    }
}
