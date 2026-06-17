package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单和权限实体类
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class Menu {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 菜单ID
     */
    private String menuId;

    /**
     * 父菜单ID（0表示顶级）
     */
    private String parentId;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 权限标识
     */
    private String permission;

    /**
     * 图标
     */
    private String icon;

    /**
     * 类型 M:目录 C:菜单 F:按钮
     */
    private String menuType;

    /**
     * 排序
     */
    private Integer orderNum;

    /**
     * 是否可见 0:隐藏 1:显示
     */
    private Integer visible;

    /**
     * 状态 0:禁用 1:正常
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 子菜单列表（非数据库字段）
     */
    private List<Menu> children;
}
