package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SalesCommissionSettlementUpdateParam {

    @NotBlank(message = "结算状态不能为空")
    private String settlementStatus;
}
