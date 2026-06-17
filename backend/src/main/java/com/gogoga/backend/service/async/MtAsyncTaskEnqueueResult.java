package com.gogoga.backend.service.async;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据库异步任务入队结果。
 */
@Getter
@AllArgsConstructor
public class MtAsyncTaskEnqueueResult {

    /**
     * 任务 ID。
     */
    private final Long taskId;

    /**
     * 是否本次新创建任务。
     */
    private final boolean created;
}
