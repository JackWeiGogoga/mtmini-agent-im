package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店算力消费来源明细实体
 */
@Data
public class MtPowerConsumeDetail {

    private Long id;

    private Long consumeId;

    private String consumeNo;

    private Long shopId;

    private String sourceBalanceType;

    private Integer powerAmount;

    private Integer balanceBefore;

    private Integer balanceAfter;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
