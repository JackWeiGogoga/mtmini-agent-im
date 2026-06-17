package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色菜单关联实体类
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class RoleMenu {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 角色ID
     */
    private String roleId;

    /**
     * 菜单ID
     */
    private String menuId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
