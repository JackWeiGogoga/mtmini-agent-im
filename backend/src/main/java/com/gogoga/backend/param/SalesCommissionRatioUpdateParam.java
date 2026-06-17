package com.gogoga.backend.param;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SalesCommissionRatioUpdateParam {

    @NotNull(message = "佣金比例不能为空")
    @Min(value = 0, message = "佣金比例不能小于 0")
    @Max(value = 100, message = "佣金比例不能大于 100")
    private Integer commissionRatio;
}
