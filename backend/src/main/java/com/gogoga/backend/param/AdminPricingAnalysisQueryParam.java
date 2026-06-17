package com.gogoga.backend.param;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台定价分析查询参数。
 */
@Data
public class AdminPricingAnalysisQueryParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long shopId;

    private String poiId;

    private String shopName;

    private String keyword;

    private String executeStatus;

    private String decisionAction;

    private String decisionReason;

    private String logMarkdownStatus;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime slotStartTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime slotEndTime;

    private Integer pageNum;

    private Integer pageSize;
}
