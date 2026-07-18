package com.gogoga.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台手动拉取美团订阅订单结果。
 */
@Data
@Builder
public class AdminMtAppOrderManualPullVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer appId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer processedPages;

    private Integer totalFetched;

    private Integer createdCount;

    private Integer updatedCount;

    private Integer skippedCount;

    private Integer appliedRightsCount;
}
