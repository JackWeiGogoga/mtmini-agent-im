package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新菜单请求参数
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class MenuUpdateParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    @NotBlank(message = "菜单ID不能为空")
    private String menuId;

    /**
     * 父菜单ID
     */
    private String parentId;

    /**
     * 菜单名称
     */
    @Size(max = 64, message = "菜单名称长度不能超过64个字符")
    private String menuName;

    /**
     * 路由路径
     */
    @Size(max = 128, message = "路由路径长度不能超过128个字符")
    private String path;

    /**
     * 组件路径
     */
    @Size(max = 256, message = "组件路径长度不能超过256个字符")
    private String component;

    /**
     * 权限标识
     */
    @Size(max = 128, message = "权限标识长度不能超过128个字符")
    private String perms;

    /**
     * 图标
     */
    @Size(max = 64, message = "图标长度不能超过64个字符")
    private String icon;

    /**
     * 菜单类型（M：目录，C：菜单，F：按钮）
     */
    @Size(max = 1, message = "菜单类型长度不能超过1个字符")
    private String menuType;

    /**
     * 状态（0：禁用，1：启用）
     */
    private Integer status;

    /**
     * 排序值
     */
    private Integer orderNum;

    /**
     * 是否可见（0：隐藏，1：显示）
     */
    private Integer visible;
}
