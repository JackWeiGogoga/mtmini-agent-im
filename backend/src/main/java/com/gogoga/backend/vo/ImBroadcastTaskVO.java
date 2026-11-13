package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 管理后台群发批次列表项。
 */
@Data
public class ImBroadcastTaskVO {
    private Long taskId;
    private Long shopId;
    private String shopName;
    private String poiId;
    private String epoiId;
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
}
