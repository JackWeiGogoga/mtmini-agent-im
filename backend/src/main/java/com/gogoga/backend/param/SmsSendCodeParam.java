package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送短信验证码参数
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class SmsSendCodeParam {

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;
}
