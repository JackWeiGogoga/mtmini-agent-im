package com.gogoga.backend.service.async;

/**
 * 业务已经记录为失败，异步任务应直接进入终态失败，不再自动重试。
 */
public class MtAsyncTaskTerminalFailureException extends RuntimeException {

    public MtAsyncTaskTerminalFailureException(String message) {
        super(message);
    }
}
