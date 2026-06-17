package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建用户请求参数
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class UserCreateParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64个字符")
    private String username;

    /**
     * 用户昵称
     */
    @Size(max = 64, message = "昵称长度不能超过64个字符")
    private String nickname;

    /**
     * 邮箱
     */
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;

    /**
     * 手机号
     */
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;

    /**
     * 头像URL
     */
    @Size(max = 256, message = "头像URL长度不能超过256个字符")
    private String avatar;

    /**
     * 状态（0：禁用，1：启用）
     */
    private Integer status;

    /**
     * 角色ID列表
     */
    private List<String> roleIds;
}
