package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台算力消费记录视图
 */
@Data
public class AdminPowerConsumeRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String consumeNo;

    private Long shopId;

    private String sourceType;

    private String sourceTypeText;

    private String sourceId;

    private String requestNo;

    private Integer powerAmount;

    private Integer balanceBefore;

    private Integer balanceAfter;

    private String remark;

    private LocalDateTime createTime;

    private List<AdminPowerConsumeDetailVO> details;
}
