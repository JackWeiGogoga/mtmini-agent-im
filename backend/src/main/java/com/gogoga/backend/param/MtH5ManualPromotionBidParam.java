package com.gogoga.backend.param;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * H5 手动修改当前推广出价参数
 *
 * @author system
 * @since 2026-05-11
 */
@Data
public class MtH5ManualPromotionBidParam {

    @NotNull(message = "出价不能为空")
    @DecimalMin(value = "0.1", message = "出价不能低于0.1元")
    @DecimalMax(value = "4.0", message = "出价不能高于4元")
    private BigDecimal bidAmount;
}
