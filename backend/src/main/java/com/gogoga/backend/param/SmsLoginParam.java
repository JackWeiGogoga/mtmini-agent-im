package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 短信验证码登录参数
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class SmsLoginParam {

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 记住我（延长会话时间）
     */
    private Boolean rememberMe = false;

    /**
     * 指定后台工作台（可选）
     */
    private String portal;
}
