package com.gogoga.backend.service.async;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

/**
 * 异步任务执行策略。
 */
@Value
@Builder
public class MtAsyncTaskExecutionPolicy {

    int maxRetryCount;

    Duration timeout;

    Duration initialBackoff;

    Duration maxBackoff;
}
