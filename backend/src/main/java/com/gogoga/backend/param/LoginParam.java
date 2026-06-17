package com.gogoga.backend.param;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮箱密码登录参数
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class LoginParam {

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 记住我（延长会话时间）
     */
    private Boolean rememberMe = false;

    /**
     * 指定后台工作台（可选）
     */
    private String portal;
}
