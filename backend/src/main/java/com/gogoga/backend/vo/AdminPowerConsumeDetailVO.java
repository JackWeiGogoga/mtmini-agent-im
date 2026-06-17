package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 后台算力消费来源拆分视图
 */
@Data
public class AdminPowerConsumeDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sourceBalanceType;

    private String sourceBalanceTypeText;

    private Integer powerAmount;

    private Integer balanceBefore;

    private Integer balanceAfter;
}
