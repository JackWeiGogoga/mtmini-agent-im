package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台门店算力发放记录视图
 */
@Data
public class AdminPowerGrantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String grantNo;

    private Long shopId;

    private String poiId;

    private String shopName;

    private Integer powerAmount;

    private String grantReason;

    private String remark;

    private String operatorUserId;

    private String operatorUsername;

    private String status;

    private LocalDateTime createTime;
}
