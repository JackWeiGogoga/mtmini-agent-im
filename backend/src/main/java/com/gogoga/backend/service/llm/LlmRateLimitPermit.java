package com.gogoga.backend.service.llm;

/**
 * Redis GCRA 的单次判定结果。
 */
public record LlmRateLimitPermit(boolean allowed, long retryAfterMillis) {

    public static LlmRateLimitPermit granted() {
        return new LlmRateLimitPermit(true, 0L);
    }

    public static LlmRateLimitPermit denied(long retryAfterMillis) {
        return new LlmRateLimitPermit(false, Math.max(retryAfterMillis, 1L));
    }
}
