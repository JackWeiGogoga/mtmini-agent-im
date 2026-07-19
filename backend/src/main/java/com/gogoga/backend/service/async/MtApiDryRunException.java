package com.gogoga.backend.service.async;

import lombok.Getter;

/**
 * 全局美团 SDK 出站调用被 dry run 阻断的信号。
 *
 * <p>继承数据库任务 dry-run 异常，使异步任务能够无损重排；同步入口则快速失败，
 * 且只暴露安全的操作名称。</p>
 */
@Getter
public class MtApiDryRunException extends MtAsyncTaskDryRunException {

    public static final String ERROR_CODE = "MT_API_DRY_RUN";

    private final String operation;

    public MtApiDryRunException(String operation, long rescheduleSeconds) {
        super(ERROR_CODE + ":" + operation, rescheduleSeconds);
        this.operation = operation;
    }
}
