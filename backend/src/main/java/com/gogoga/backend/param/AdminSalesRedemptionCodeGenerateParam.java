package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminSalesRedemptionCodeGenerateParam {

    @NotBlank(message = "手机号不能为空")
    private String phone;
}
