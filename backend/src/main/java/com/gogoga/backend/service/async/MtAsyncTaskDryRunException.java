package com.gogoga.backend.service.async;

import lombok.Getter;

/**
 * 数据库异步任务 dry run 跳过执行信号。
 */
@Getter
public class MtAsyncTaskDryRunException extends RuntimeException {

    private final long rescheduleSeconds;

    public MtAsyncTaskDryRunException(String message, long rescheduleSeconds) {
        super(message);
        this.rescheduleSeconds = Math.max(rescheduleSeconds, 1L);
    }
}
