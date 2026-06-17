package com.gogoga.backend.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * H5 算力充值创建参数
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class MtH5PowerRechargeCreateParam {

    /**
     * 购买算力
     */
    @NotNull(message = "购买算力不能为空")
    private Integer powerAmount;

    private String redemptionCode;
}
