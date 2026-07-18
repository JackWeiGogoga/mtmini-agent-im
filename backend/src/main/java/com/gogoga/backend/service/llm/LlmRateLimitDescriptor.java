package com.gogoga.backend.service.llm;

/**
 * 单次模型调用的限流维度。禁止将 apiKey 输出到日志。
 */
public record LlmRateLimitDescriptor(
        String provider,
        String apiKey,
        String model,
        String operation,
        boolean interactive
) {

    public static LlmRateLimitDescriptor background(
            String provider, String apiKey, String model, String operation) {
        return new LlmRateLimitDescriptor(provider, apiKey, model, operation, false);
    }

    public static LlmRateLimitDescriptor interactive(
            String provider, String apiKey, String model, String operation) {
        return new LlmRateLimitDescriptor(provider, apiKey, model, operation, true);
    }
}
