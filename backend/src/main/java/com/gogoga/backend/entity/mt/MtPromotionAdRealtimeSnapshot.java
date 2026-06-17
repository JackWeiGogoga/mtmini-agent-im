package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店推广广告实时快照
 */
@Data
public class MtPromotionAdRealtimeSnapshot {

    private Long id;

    private Long shopId;

    private LocalDate bizDate;

    private LocalDateTime slotTime;

    private Integer channel;

    private String currentHourRange;

    private Integer hourShowCount;

    private Integer hourClickCount;

    private Long hourDebitCents;

    private Integer dayShowCount;

    private Integer dayClickCount;

    private Long dayDebitCents;

    private String payloadJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
