package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分配角色请求参数
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class AssignRolesParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * 角色ID列表
     */
    @NotEmpty(message = "角色列表不能为空")
    private List<String> roleIds;
}
