package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 后台门店算力发放创建参数
 */
@Data
public class AdminPowerGrantCreateParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "店铺ID不能为空")
    private Long shopId;

    @NotNull(message = "发放算力不能为空")
    @Positive(message = "发放算力必须大于0")
    private Integer powerAmount;

    @NotBlank(message = "发放原因不能为空")
    private String grantReason;

    private String remark;
}
