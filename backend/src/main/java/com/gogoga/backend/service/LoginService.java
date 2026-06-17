package com.gogoga.backend.service;

import java.util.List;
import java.util.Set;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.entity.mt.MtShop;

/**
 * 登录服务接口
 *
 * @author system
 * @since 2025-01-21
 */
public interface LoginService {

    /**
     * 处理登录
     *
     * @param userId   用户ID
     * @param userName 用户姓名
     * @param email    邮箱
     * @return 登录用户信息
     */
    LoginUser login(String userId, String userName, String email);

    /**
     * 使用美团门店身份登录
     *
     * @param shop 美团店铺
     * @return 登录用户信息
     */
    LoginUser loginMtShop(MtShop shop);

    /**
     * 处理登出
     *
     * @param token 令牌
     */
    void logout(String token);

    /**
     * 获取当前用户信息
     *
     * @return 登录用户信息
     */
    LoginUser getCurrentUser();

    /**
     * 获取当前用户菜单树
     *
     * @return 菜单列表（树形结构）
     */
    List<Menu> getCurrentUserMenus();

    /**
     * 获取当前用户权限列表
     *
     * @return 权限标识列表
     */
    List<String> getCurrentUserPermissions();

    /**
     * 获取指定用户的角色集合
     *
     * @param userId 用户ID
     * @return 角色代码集合
     */
    Set<String> getUserRoles(String userId);

    /**
     * 获取指定用户的权限集合
     *
     * @param userId 用户ID
     * @return 权限标识集合
     */
    Set<String> getUserPermissions(String userId);
}
