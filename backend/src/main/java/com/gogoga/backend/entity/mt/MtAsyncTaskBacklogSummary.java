package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库异步任务积压摘要。
 */
@Data
public class MtAsyncTaskBacklogSummary {

    private String taskType;

    private Long pendingCount;

    private LocalDateTime oldestPendingAt;

    private Long readyCount;

    private Long processingCount;

    private Long successCount;

    private Long deadCount;
}
