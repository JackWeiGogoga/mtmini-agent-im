package com.gogoga.backend.service.llm;

/**
 * 模型容量暂不可用；异步任务应无损重排，不能按业务失败处理。
 */
public class LlmCapacityDeferredException extends RuntimeException {

    private final long retryAfterMillis;

    public LlmCapacityDeferredException(long retryAfterMillis) {
        this("LLM_CAPACITY_DEFERRED", retryAfterMillis, null);
    }

    public LlmCapacityDeferredException(String message, long retryAfterMillis, Throwable cause) {
        super(message, cause);
        this.retryAfterMillis = Math.max(retryAfterMillis, 1L);
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
