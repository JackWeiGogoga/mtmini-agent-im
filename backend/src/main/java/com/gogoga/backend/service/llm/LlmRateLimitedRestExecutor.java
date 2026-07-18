package com.gogoga.backend.service.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 为直接 RestClient 模型出口提供统一限流和 429 冷却。
 */
@Component
@RequiredArgsConstructor
public class LlmRateLimitedRestExecutor {

    private final LlmClusterRateLimiter rateLimiter;
    private final LlmRateLimitRetryAfterParser retryAfterParser;

    public <T> T execute(LlmRateLimitDescriptor descriptor, Supplier<T> request) {
        rateLimiter.acquire(descriptor);
        try {
            return request.get();
        } catch (RestClientResponseException firstException) {
            if (!isRateLimit(firstException)) {
                throw firstException;
            }
            long retryAfterMillis = retryAfterMillis(firstException);
            rateLimiter.registerProviderRateLimit(descriptor, retryAfterMillis);
            rateLimiter.acquire(descriptor);
            try {
                return request.get();
            } catch (RestClientResponseException secondException) {
                if (!isRateLimit(secondException)) {
                    throw secondException;
                }
                long secondRetryAfterMillis = retryAfterMillis(secondException);
                rateLimiter.registerProviderRateLimit(descriptor, secondRetryAfterMillis);
                throw new LlmCapacityDeferredException(
                        "LLM_PROVIDER_RATE_LIMITED",
                        secondRetryAfterMillis,
                        secondException
                );
            }
        }
    }

    private boolean isRateLimit(RestClientResponseException exception) {
        return exception.getStatusCode().value() == 429;
    }

    private long retryAfterMillis(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        return retryAfterParser.parseMillis(retryAfter, Duration.ofMillis(rateLimiter.defaultCooldownMillis()));
    }
}
