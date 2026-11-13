package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 门店 IM 定时群发配置，一店一条。
 */
@Data
public class MtImBroadcastSetting {

    private Long id;
    private Long shopId;
    private Boolean enabled;
    private String weekDays;
    private String repeatMode;
    private LocalTime sendTime;
    private String content;
    private Long totalSentCount;
    private LocalDate lastTriggerDate;
    private Long lastTaskId;
    private String operatorUserId;
    private String operatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
