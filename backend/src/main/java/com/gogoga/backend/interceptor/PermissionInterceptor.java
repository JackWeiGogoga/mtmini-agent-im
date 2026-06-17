package com.gogoga.backend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.ResultVO;

import java.io.IOException;

/**
 * 权限拦截器
 * 处理 @RequiresPermission 注解的权限校验
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理 Controller 方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 获取权限注解
        RequiresPermission requiresPermission = getRequiresPermission(handlerMethod);
        if (requiresPermission == null) {
            return true;
        }

        // 获取当前登录用户
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null) {
            writeForbiddenResponse(response, "请先登录");
            return false;
        }

        // 总后台超级管理员直接放行；切换到工作台后按工作台过滤后的权限校验。
        if (loginUser.hasSuperAdminPermissionBypass()) {
            return true;
        }

        // 校验权限
        String permValue = requiresPermission.value();
        String[] permissions = permValue.split(",");
        RequiresPermission.Logical logical = requiresPermission.logical();

        boolean hasPermission = checkPermission(loginUser, permissions, logical);
        if (!hasPermission) {
            log.warn("用户 {} 无权访问 {}，需要权限: {}", loginUser.getUserId(), 
                    request.getRequestURI(), permValue);
            writeForbiddenResponse(response, "无权访问此资源");
            return false;
        }

        return true;
    }

    /**
     * 获取 @RequiresPermission 注解
     * 优先方法级别，其次类级别
     */
    private RequiresPermission getRequiresPermission(HandlerMethod handlerMethod) {
        // 方法级别注解
        RequiresPermission annotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (annotation != null) {
            return annotation;
        }
        // 类级别注解
        return handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
    }

    /**
     * 检查权限
     */
    private boolean checkPermission(LoginUser loginUser, String[] permissions, RequiresPermission.Logical logical) {
        if (logical == RequiresPermission.Logical.AND) {
            // 需要全部权限
            for (String perm : permissions) {
                if (!loginUser.hasPermission(perm.trim())) {
                    return false;
                }
            }
            return true;
        } else {
            // 满足其一即可
            for (String perm : permissions) {
                if (loginUser.hasPermission(perm.trim())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 写入禁止访问响应
     */
    private void writeForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ResultVO<Void> result = ResultVO.error(HttpStatus.FORBIDDEN.value(), message);
        response.getWriter().write(JsonUtil.toJson(result));
    }
}
