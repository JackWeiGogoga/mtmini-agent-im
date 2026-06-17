package com.gogoga.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.gogoga.backend.interceptor.LoginInterceptor;
import com.gogoga.backend.interceptor.PermissionInterceptor;
import com.gogoga.backend.interceptor.ShopRightInterceptor;

/**
 * Web MVC 配置
 *
 * @author system
 * @since 2025-01-21
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Autowired
    private ShopRightInterceptor shopRightInterceptor;

    /**
     * 不需要拦截的路径
     */
    private static final String[] EXCLUDE_PATHS = {
            // 认证相关（无需登录）
            "/api/auth/login",
            "/api/auth/login/password",
            "/api/auth/register",
            "/api/auth/logout",
            "/api/auth/callback",
            "/api/auth/oauth/**",
            "/api/mt/auth/login",
            "/api/callbacks/**",
            // 静态资源和错误页
            "/error",
            "/favicon.jpg",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(1);

        // 权限拦截器
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(2);

        registry.addInterceptor(shopRightInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(3);
    }

}
