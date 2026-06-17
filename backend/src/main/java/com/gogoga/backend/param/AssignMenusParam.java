package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分配菜单权限请求参数
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class AssignMenusParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色ID
     */
    @NotBlank(message = "角色ID不能为空")
    private String roleId;

    /**
     * 菜单ID列表
     */
    @NotEmpty(message = "菜单列表不能为空")
    private List<String> menuIds;
}
