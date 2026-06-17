package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店算力后台发放实体
 */
@Data
public class MtPowerGrant {

    private Long id;

    private String grantNo;

    private Long shopId;

    private Integer powerAmount;

    private String grantReason;

    private String remark;

    private String operatorUserId;

    private String operatorUsername;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
