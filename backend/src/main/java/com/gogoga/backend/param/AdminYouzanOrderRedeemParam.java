package com.gogoga.backend.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 后台有赞订单核销参数
 */
@Data
public class AdminYouzanOrderRedeemParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "有赞订单ID不能为空")
    private Long orderId;

    @NotNull(message = "美团门店ID不能为空")
    private Long mtShopId;

    private String remark;
}
