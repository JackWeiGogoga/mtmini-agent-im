package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新角色请求参数
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class RoleUpdateParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色ID
     */
    @NotBlank(message = "角色ID不能为空")
    private String roleId;

    /**
     * 角色名称
     */
    @Size(max = 64, message = "角色名称长度不能超过64个字符")
    private String roleName;

    /**
     * 角色描述
     */
    @Size(max = 256, message = "角色描述长度不能超过256个字符")
    private String description;

    /**
     * 状态（0：禁用，1：启用）
     */
    private Integer status;

    /**
     * 排序值
     */
    private Integer orderNum;

    /**
     * 菜单ID列表
     */
    private List<String> menuIds;
}
