package com.gogoga.backend.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookie 工具类
 *
 * @author system
 * @since 2025-01-21
 */
public class CookieUtil {

    /**
     * 登录令牌 Cookie 名称
     */
    public static final String TOKEN_COOKIE_NAME = "BACKEND_TOKEN";

    /**
     * Cookie 默认过期时间（7天）
     */
    public static final int DEFAULT_MAX_AGE = 7 * 24 * 60 * 60;

    /**
     * 设置 Cookie
     *
     * @param response HTTP 响应
     * @param name     Cookie 名称
     * @param value    Cookie 值
     * @param maxAge   过期时间（秒）
     */
    public static void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * 设置登录令牌 Cookie（使用默认过期时间）
     *
     * @param response HTTP 响应
     * @param token    登录令牌
     */
    public static void setTokenCookie(HttpServletResponse response, String token) {
        setCookie(response, TOKEN_COOKIE_NAME, token, DEFAULT_MAX_AGE);
    }

    /**
     * 设置登录令牌 Cookie（指定过期时间）
     *
     * @param response HTTP 响应
     * @param token    登录令牌
     * @param maxAge   过期时间（秒）
     */
    public static void setTokenCookie(HttpServletResponse response, String token, int maxAge) {
        setCookie(response, TOKEN_COOKIE_NAME, token, maxAge);
    }

    /**
     * 获取 Cookie 值
     *
     * @param request HTTP 请求
     * @param name    Cookie 名称
     * @return Cookie 值，不存在返回 null
     */
    public static String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 获取登录令牌
     *
     * @param request HTTP 请求
     * @return 登录令牌，不存在返回 null
     */
    public static String getToken(HttpServletRequest request) {
        // 优先从 Header 获取
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        // 从 Cookie 获取
        return getCookie(request, TOKEN_COOKIE_NAME);
    }

    /**
     * 删除 Cookie
     *
     * @param response HTTP 响应
     * @param name     Cookie 名称
     */
    public static void deleteCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    /**
     * 删除登录令牌 Cookie
     *
     * @param response HTTP 响应
     */
    public static void deleteTokenCookie(HttpServletResponse response) {
        deleteCookie(response, TOKEN_COOKIE_NAME);
    }
}
