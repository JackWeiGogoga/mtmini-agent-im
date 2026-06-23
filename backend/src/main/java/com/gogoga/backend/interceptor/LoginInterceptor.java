package com.gogoga.backend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.entity.AuthSessionInfo;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.service.AuthService;
import com.gogoga.backend.service.BackendPortalService;
import com.gogoga.backend.service.LoginService;
import com.gogoga.backend.service.TokenService;
import com.gogoga.backend.util.CookieUtil;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.ResultVO;

import java.io.IOException;
import java.util.Set;

/**
 * 登录拦截器
 * 处理登录校验，支持开发环境自动登录
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;

    private final AuthService authService;

    private final LoginService loginService;

    private final BackendPortalService backendPortalService;

    public LoginInterceptor(TokenService tokenService,
                            AuthService authService,
                            LoginService loginService,
                            BackendPortalService backendPortalService) {
        this.tokenService = tokenService;
        this.authService = authService;
        this.loginService = loginService;
        this.backendPortalService = backendPortalService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 清理上次请求的用户上下文
        UserContext.clear();

        // 只处理 Controller 方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 尝试获取 Token 并设置用户上下文
        String token = CookieUtil.getToken(request);
        LoginUser loginUser = null;

        if (token != null && !token.isEmpty()) {
            // 优先使用原有的 TokenService（OpenID 登录）
            loginUser = tokenService.getLoginUser(token);
            
            // 如果 TokenService 没有找到，尝试使用新的 AuthService（邮箱密码/OAuth 登录）
            if (loginUser == null) {
                loginUser = getLoginUserFromAuthService(token);
            }
            
            if (loginUser != null) {
                UserContext.setLoginUser(loginUser);
            }
        }

        // 检查是否需要登录校验
        RequiresLogin requiresLogin = getRequiresLogin(handlerMethod);
        if (requiresLogin != null && requiresLogin.required()) {
            if (UserContext.getLoginUser() == null) {
                writeUnauthorizedResponse(response, "请先登录");
                return false;
            }
        }

        return true;
    }

    /**
     * 从 AuthService 获取登录用户信息
     * 用于支持邮箱密码登录和 OAuth 登录
     */
    private LoginUser getLoginUserFromAuthService(String token) {
        AuthSessionInfo sessionInfo = authService.getSessionInfo(token);
        if (sessionInfo == null || sessionInfo.getUserId() == null) {
            return null;
        }
        User user = authService.getEffectiveUserBySession(token);
        if (user == null) {
            return null;
        }

        Set<String> roles = loginService.getUserRoles(user.getUserId());
        BackendPortalType activePortal = backendPortalService.resolveSessionPortal(user, roles, sessionInfo.getActivePortal());
        Set<String> permissions = backendPortalService.filterPermissions(
                loginService.getUserPermissions(user.getUserId()),
                activePortal
        );

        // 将 User 转换为 LoginUser
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUserName(user.getNickname() != null ? user.getNickname() : user.getUsername());
        loginUser.setEmail(user.getEmail());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setToken(token);
        loginUser.setLoginTime(System.currentTimeMillis());

        loginUser.setRoles(roles);
        loginUser.setPermissions(permissions);
        loginUser.setActivePortal(activePortal);
        loginUser.setAvailablePortals(backendPortalService.resolveAvailablePortals(user, roles, activePortal));
        loginUser.setImpersonated(sessionInfo.getImpersonatedUserId() != null);
        loginUser.setImpersonatorUserId(sessionInfo.getImpersonatedUserId() == null ? null : sessionInfo.getUserId());

        return loginUser;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束后清理用户上下文
        UserContext.clear();
    }

    /**
     * 获取 @RequiresLogin 注解
     * 优先方法级别，其次类级别
     */
    private RequiresLogin getRequiresLogin(HandlerMethod handlerMethod) {
        // 方法级别注解
        RequiresLogin annotation = handlerMethod.getMethodAnnotation(RequiresLogin.class);
        if (annotation != null) {
            return annotation;
        }
        // 类级别注解
        return handlerMethod.getBeanType().getAnnotation(RequiresLogin.class);
    }

    /**
     * 写入未授权响应
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ResultVO<Void> result = ResultVO.error(HttpStatus.UNAUTHORIZED.value(), message);
        response.getWriter().write(JsonUtil.toJson(result));
    }
}
