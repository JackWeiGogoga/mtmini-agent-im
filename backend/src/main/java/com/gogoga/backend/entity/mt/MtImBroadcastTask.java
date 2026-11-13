package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * IM 定时群发的一次触发批次。
 */
@Data
public class MtImBroadcastTask {

    private Long id;
    private Long shopId;
    private Long settingId;
    private LocalDate triggerDate;
    private LocalTime planTime;
    private String content;
    private String weekDays;
    private String repeatMode;
    private String status;
    private Integer targetCount;
    private Integer truncatedCount;
    private Integer shardCount;
    private Integer successCount;
    private Integer failCount;
    private String skipReason;
    private LocalDateTime plannedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
