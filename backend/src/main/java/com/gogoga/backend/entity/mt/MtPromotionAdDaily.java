package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店推广广告日汇总数据
 */
@Data
public class MtPromotionAdDaily {

    private Long id;

    private Long shopId;

    private LocalDate bizDate;

    private Integer channel;

    private Integer showCount;

    private Integer clickCount;

    private Long debitCents;

    private String payloadJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
