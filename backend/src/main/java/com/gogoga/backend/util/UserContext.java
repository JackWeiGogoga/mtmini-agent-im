package com.gogoga.backend.util;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.enums.BackendPortalType;

/**
 * 用户上下文工具类
 * 使用 ThreadLocal 存储当前登录用户信息
 *
 * @author system
 * @since 2025-01-21
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> LOGIN_USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前登录用户
     *
     * @param loginUser 登录用户
     */
    public static void setLoginUser(LoginUser loginUser) {
        LOGIN_USER_HOLDER.set(loginUser);
    }

    /**
     * 获取当前登录用户
     *
     * @return 登录用户，未登录返回 null
     */
    public static LoginUser getLoginUser() {
        return LOGIN_USER_HOLDER.get();
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID，未登录返回 null
     */
    public static String getUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUserId() : null;
    }

    /**
     * 获取当前用户姓名
     *
     * @return 用户姓名，未登录返回 null
     */
    public static String getUserName() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUserName() : null;
    }

    /**
     * 判断当前是否已登录
     *
     * @return true 已登录
     */
    public static boolean isLoggedIn() {
        return getLoginUser() != null;
    }

    /**
     * 判断当前用户是否超级管理员
     *
     * @return true 是超级管理员
     */
    public static boolean isSuperAdmin() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null && loginUser.isSuperAdmin();
    }

    /**
     * 检查当前用户是否拥有指定权限
     *
     * @param permission 权限标识
     * @return true 拥有权限
     */
    public static boolean hasPermission(String permission) {
        LoginUser loginUser = getLoginUser();
        return loginUser != null && loginUser.hasPermission(permission);
    }

    /**
     * 获取当前后台工作台
     *
     * @return 工作台上下文
     */
    public static BackendPortalType getActivePortal() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getActivePortal();
    }

    /**
     * 清除当前登录用户
     */
    public static void clear() {
        LOGIN_USER_HOLDER.remove();
    }
}
