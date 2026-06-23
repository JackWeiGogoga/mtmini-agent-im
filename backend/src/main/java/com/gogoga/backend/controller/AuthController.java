package com.gogoga.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.config.OAuthProperties;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.AuthProviderEnum;
import com.gogoga.backend.param.AdminImpersonationParam;
import com.gogoga.backend.param.BackendPortalSwitchParam;
import com.gogoga.backend.param.LoginParam;
import com.gogoga.backend.param.RegisterParam;
import com.gogoga.backend.param.SmsLoginParam;
import com.gogoga.backend.param.SmsSendCodeParam;
import com.gogoga.backend.service.AuthService;
import com.gogoga.backend.service.BackendPortalService;
import com.gogoga.backend.service.LoginService;
import com.gogoga.backend.service.auth.AuthResult;
import com.gogoga.backend.util.CookieUtil;
import com.gogoga.backend.util.IPUtil;
import com.gogoga.backend.vo.ResultVO;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;

/**
 * 认证控制器
 * 
 * 支持多种认证方式：
 * - 邮箱密码登录/注册
 * - 手机验证码登录
 * - Google OAuth 登录
 * - GitHub OAuth 登录
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;

    private final AuthService authService;

    private final BackendPortalService backendPortalService;

    private final OAuthProperties oAuthProperties;

    public AuthController(LoginService loginService,
                          AuthService authService,
                          BackendPortalService backendPortalService,
                          OAuthProperties oAuthProperties) {
        this.loginService = loginService;
        this.authService = authService;
        this.backendPortalService = backendPortalService;
        this.oAuthProperties = oAuthProperties;
    }

    // ==================== 邮箱密码认证 ====================

    /**
     * 邮箱密码注册
     */
    @PostMapping("/register")
    public ResultVO<Map<String, Object>> register(
            @RequestBody RegisterParam registerParam,
            HttpServletResponse response) {
        
        AuthResult result = authService.register(registerParam);
        
        if (!result.isSuccess()) {
            return ResultVO.error(400, result.getErrorMessage());
        }

        // 设置 Token Cookie
        setAuthCookie(response, result.getSessionToken(), 
                result.getSessionExpireSeconds().intValue());

        return ResultVO.success(buildAuthResponse(result));
    }

    /**
     * 邮箱密码登录
     */
    @PostMapping("/login/password")
    public ResultVO<Map<String, Object>> loginWithPassword(
            @RequestBody LoginParam loginParam,
            HttpServletResponse response) {
        
        AuthResult result = authService.loginWithPassword(loginParam);
        
        if (!result.isSuccess()) {
            return ResultVO.error(401, result.getErrorMessage());
        }

        // 设置 Token Cookie
        setAuthCookie(response, result.getSessionToken(), 
                result.getSessionExpireSeconds().intValue());

        return ResultVO.success(buildAuthResponse(result));
    }

    /**
     * 发送短信验证码
     */
    @PostMapping("/sms/send-code")
    public ResultVO<Void> sendSmsCode(
            @RequestBody SmsSendCodeParam param,
            HttpServletRequest request) {
        authService.sendSmsCode(param.getPhone(), IPUtil.getRealIp(request));
        return ResultVO.success();
    }

    /**
     * 手机验证码登录
     */
    @PostMapping("/login/sms")
    public ResultVO<Map<String, Object>> loginWithSms(
            @RequestBody SmsLoginParam smsLoginParam,
            HttpServletResponse response) {

        AuthResult result = authService.loginWithSms(smsLoginParam);

        if (!result.isSuccess()) {
            return ResultVO.error(401, result.getErrorMessage());
        }

        setAuthCookie(response, result.getSessionToken(),
                result.getSessionExpireSeconds().intValue());

        return ResultVO.success(buildAuthResponse(result));
    }

    // ==================== OAuth 认证 ====================

    /**
     * 获取 OAuth 配置信息（前端构建授权 URL 用）
     */
    @GetMapping("/oauth/config")
    public ResultVO<Map<String, Object>> getOAuthConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // Google OAuth 配置
        Map<String, String> googleConfig = new HashMap<>();
        googleConfig.put("clientId", oAuthProperties.getGoogle().getClientId());
        googleConfig.put("authorizationUri", oAuthProperties.getGoogle().getAuthorizationUri());
        googleConfig.put("redirectUri", oAuthProperties.getGoogle().getRedirectUri());
        googleConfig.put("scope", oAuthProperties.getGoogle().getScope());
        config.put("google", googleConfig);
        
        // GitHub OAuth 配置
        Map<String, String> githubConfig = new HashMap<>();
        githubConfig.put("clientId", oAuthProperties.getGithub().getClientId());
        githubConfig.put("authorizationUri", oAuthProperties.getGithub().getAuthorizationUri());
        githubConfig.put("redirectUri", oAuthProperties.getGithub().getRedirectUri());
        githubConfig.put("scope", oAuthProperties.getGithub().getScope());
        config.put("github", githubConfig);
        
        return ResultVO.success(config);
    }

    /**
     * Google OAuth 回调
     */
    @GetMapping("/oauth/google/callback")
    public void googleOAuthCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, defaultValue = "false") boolean rememberMe,
            HttpServletResponse response) throws IOException {
        
        handleOAuthCallback(AuthProviderEnum.GOOGLE, code, 
                oAuthProperties.getGoogle().getRedirectUri(), rememberMe, response);
    }

    /**
     * Google OAuth 回调（POST 方式，用于前端 SPA）
     */
    @PostMapping("/oauth/google/callback")
    public ResultVO<Map<String, Object>> googleOAuthCallbackPost(
            @RequestParam String code,
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false, defaultValue = "false") boolean rememberMe,
            HttpServletResponse response) {
        
        return handleOAuthCallbackPost(AuthProviderEnum.GOOGLE, code, 
                redirectUri != null ? redirectUri : oAuthProperties.getGoogle().getRedirectUri(), 
                rememberMe, response);
    }

    /**
     * GitHub OAuth 回调
     */
    @GetMapping("/oauth/github/callback")
    public void githubOAuthCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, defaultValue = "false") boolean rememberMe,
            HttpServletResponse response) throws IOException {
        
        handleOAuthCallback(AuthProviderEnum.GITHUB, code, 
                oAuthProperties.getGithub().getRedirectUri(), rememberMe, response);
    }

    /**
     * GitHub OAuth 回调（POST 方式，用于前端 SPA）
     */
    @PostMapping("/oauth/github/callback")
    public ResultVO<Map<String, Object>> githubOAuthCallbackPost(
            @RequestParam String code,
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false, defaultValue = "false") boolean rememberMe,
            HttpServletResponse response) {
        
        return handleOAuthCallbackPost(AuthProviderEnum.GITHUB, code, 
                redirectUri != null ? redirectUri : oAuthProperties.getGithub().getRedirectUri(), 
                rememberMe, response);
    }

    // ==================== 原有 OpenID 认证 ====================

    /**
     * 登出
     */
    @PostMapping("/logout")
    public ResultVO<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = CookieUtil.getToken(request);
        
        // 同时处理两种 session 方式
        loginService.logout(token);
        authService.logout(token);

        // 删除 Cookie
        CookieUtil.deleteTokenCookie(response);

        return ResultVO.success();
    }

    /**
     * 获取当前用户信息（包含菜单和权限）
     */
    @GetMapping("/current-user")
    @RequiresLogin
    public ResultVO<Map<String, Object>> getCurrentUser() {
        LoginUser loginUser = loginService.getCurrentUser();
        List<Menu> menus = loginService.getCurrentUserMenus();
        List<String> permissions = loginService.getCurrentUserPermissions();

        Map<String, Object> result = new HashMap<>();
        result.put("userId", loginUser.getUserId());
        result.put("username", loginUser.getUserName());
        result.put("nickname", loginUser.getUserName());
        result.put("email", loginUser.getEmail());
        result.put("avatar", loginUser.getAvatar());
        result.put("roles", loginUser.getRoles());
        result.put("permissions", permissions);
        result.put("menus", menus);
        result.put("activePortal", loginUser.getActivePortal());
        result.put("availablePortals", loginUser.getAvailablePortals());
        result.put("impersonated", Boolean.TRUE.equals(loginUser.getImpersonated()));
        result.put("impersonatorUserId", loginUser.getImpersonatorUserId());

        return ResultVO.success(result);
    }

    /**
     * 切换后台工作台
     */
    @PostMapping("/switch-portal")
    @RequiresLogin
    public ResultVO<Map<String, Object>> switchPortal(@Valid @RequestBody BackendPortalSwitchParam param,
                                                      HttpServletRequest request) {
        String token = CookieUtil.getToken(request);
        BackendPortalType targetPortal = BackendPortalType.fromValue(param.getPortal());
        authService.switchPortal(token, targetPortal);

        LoginUser currentUser = loginService.getCurrentUser();
        User user = authService.getUserBySession(token);
        if (currentUser != null && user != null) {
            Set<String> roles = loginService.getUserRoles(currentUser.getUserId());
            currentUser.setRoles(roles);
            currentUser.setActivePortal(targetPortal);
            currentUser.setPermissions(backendPortalService.filterPermissions(
                    loginService.getUserPermissions(currentUser.getUserId()),
                    targetPortal
            ));
            currentUser.setAvailablePortals(backendPortalService.resolveAvailablePortals(user, roles, targetPortal));
        }
        return getCurrentUser();
    }

    @PostMapping("/impersonation")
    @RequiresLogin
    public ResultVO<Void> startImpersonation(@Valid @RequestBody AdminImpersonationParam param,
                                             HttpServletRequest request) {
        String token = CookieUtil.getToken(request);
        BackendPortalType targetPortal = BackendPortalType.fromValue(param.getPortal());
        authService.startImpersonation(token, param.getTargetUserId(), targetPortal);
        return ResultVO.success();
    }

    @PostMapping("/impersonation/stop")
    @RequiresLogin
    public ResultVO<Void> stopImpersonation(HttpServletRequest request) {
        String token = CookieUtil.getToken(request);
        authService.stopImpersonation(token);
        return ResultVO.success();
    }

    /**
     * 获取当前用户菜单
     */
    @GetMapping("/menus")
    @RequiresLogin
    public ResultVO<List<Menu>> getCurrentUserMenus() {
        List<Menu> menus = loginService.getCurrentUserMenus();
        return ResultVO.success(menus);
    }

    /**
     * 获取当前用户权限
     */
    @GetMapping("/permissions")
    @RequiresLogin
    public ResultVO<List<String>> getCurrentUserPermissions() {
        List<String> permissions = loginService.getCurrentUserPermissions();
        return ResultVO.success(permissions);
    }

    // ==================== 私有方法 ====================

    /**
     * 处理 OAuth 回调（重定向方式）
     */
    private void handleOAuthCallback(AuthProviderEnum provider, String code, 
                                      String redirectUri, boolean rememberMe,
                                      HttpServletResponse response) throws IOException {
        AuthResult result = authService.loginWithOAuth(provider, code, redirectUri, rememberMe);
        
        if (result.isSuccess()) {
            setAuthCookie(response, result.getSessionToken(), 
                    result.getSessionExpireSeconds().intValue());
            response.sendRedirect(oAuthProperties.getSuccessUrl());
        } else {
            String errorMsg = URLEncoder.encode(result.getErrorMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(oAuthProperties.getSuccessUrl() + "?error=" + result.getErrorCode() + "&message=" + errorMsg);
        }
    }

    /**
     * 处理 OAuth 回调（API 方式）
     */
    private ResultVO<Map<String, Object>> handleOAuthCallbackPost(AuthProviderEnum provider, 
                                                                   String code, String redirectUri,
                                                                   boolean rememberMe,
                                                                   HttpServletResponse response) {
        AuthResult result = authService.loginWithOAuth(provider, code, redirectUri, rememberMe);
        
        if (!result.isSuccess()) {
            return ResultVO.error(401, result.getErrorMessage());
        }

        setAuthCookie(response, result.getSessionToken(), 
                result.getSessionExpireSeconds().intValue());

        return ResultVO.success(buildAuthResponse(result));
    }

    /**
     * 设置认证 Cookie
     */
    private void setAuthCookie(HttpServletResponse response, String token, int maxAge) {
        CookieUtil.setTokenCookie(response, token, maxAge);
    }

    /**
     * 构建认证响应
     */
    private Map<String, Object> buildAuthResponse(AuthResult result) {
        Map<String, Object> data = new HashMap<>();
        User user = result.getUser();
        
        data.put("token", result.getSessionToken());
        data.put("expiresIn", result.getSessionExpireSeconds());
        data.put("isNewUser", result.isNewUser());
        data.put("authProvider", result.getProviderType().name());
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getUserId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("email", user.getEmail());
        userInfo.put("avatar", user.getAvatar());
        data.put("user", userInfo);
        
        return data;
    }

}
