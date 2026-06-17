package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店推广广告分时明细数据
 */
@Data
public class MtPromotionAdHourlyDetail {

    private Long id;

    private Long shopId;

    private LocalDate bizDate;

    private Integer channel;

    private String timeRange;

    private Integer showCount;

    private Integer clickCount;

    private Long debitCents;

    private String payloadJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
