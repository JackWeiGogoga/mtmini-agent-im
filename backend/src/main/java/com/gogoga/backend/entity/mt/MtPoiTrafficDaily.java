package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店日流量曝光与入店数据
 *
 * @author system
 * @since 2026-04-10
 */
@Data
public class MtPoiTrafficDaily {

    private Long id;

    private Long shopId;

    private LocalDate bizDate;

    private Integer flowProp;

    private Integer exposureCount;

    private Integer visitCount;

    private String payloadJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
