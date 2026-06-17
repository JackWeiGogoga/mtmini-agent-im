package com.gogoga.backend.entity;

import com.gogoga.backend.enums.BackendPortalType;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 登录用户模型，用于 Session 存储
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（邮箱前缀）
     */
    private String userId;

    /**
     * 用户姓名
     */
    private String userName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 登录令牌
     */
    private String token;

    /**
     * 登录时间（毫秒）
     */
    private Long loginTime;

    /**
     * 过期时间（毫秒）
     */
    private Long expireTime;

    /**
     * 用户角色编码集合
     */
    private Set<String> roles;

    /**
     * 用户权限标识集合
     */
    private Set<String> permissions;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 当前后台工作台
     */
    private BackendPortalType activePortal;

    /**
     * 当前账号可切换的后台工作台
     */
    private List<BackendPortalType> availablePortals;

    /**
     * 当前美团 H5 门店 epoiId
     */
    private String epoiId;

    /**
     * 当前会话是否由管理员模拟登录。
     */
    private Boolean impersonated;

    /**
     * 发起模拟登录的管理员用户 ID。
     */
    private String impersonatorUserId;

    /**
     * 是否超级管理员
     */
    public boolean isSuperAdmin() {
        return roles != null && roles.contains("super_admin");
    }

    /**
     * 总后台下超级管理员跳过细粒度权限校验；切换到工作台后按过滤后的权限集合校验。
     */
    public boolean hasSuperAdminPermissionBypass() {
        return isSuperAdmin() && (activePortal == null || activePortal == BackendPortalType.ADMIN);
    }

    /**
     * 检查是否拥有指定权限
     *
     * @param permission 权限标识
     * @return true 如果拥有权限
     */
    public boolean hasPermission(String permission) {
        if (hasSuperAdminPermissionBypass()) {
            return true;
        }
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 检查是否拥有任一权限
     *
     * @param perms 权限标识数组
     * @return true 如果拥有任一权限
     */
    public boolean hasAnyPermission(String... perms) {
        if (hasSuperAdminPermissionBypass()) {
            return true;
        }
        if (permissions == null || perms == null) {
            return false;
        }
        for (String perm : perms) {
            if (permissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }
}
