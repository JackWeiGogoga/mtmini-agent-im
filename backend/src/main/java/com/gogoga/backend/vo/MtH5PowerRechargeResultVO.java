package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * H5 算力充值结果视图对象
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class MtH5PowerRechargeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 购买算力
     */
    private Integer powerAmount;

    /**
     * 支付状态
     */
    private Integer payStatus;
}
