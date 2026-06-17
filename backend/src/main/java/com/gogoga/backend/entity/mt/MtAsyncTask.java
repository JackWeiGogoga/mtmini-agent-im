package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库异步任务实体。
 */
@Data
public class MtAsyncTask {

    private Long id;

    private String taskType;

    private String bizKey;

    private String scopeKey;

    private String payloadJson;

    private String status;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime availableAt;

    private LocalDateTime claimedAt;

    private LocalDateTime finishedAt;

    private String workerId;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
