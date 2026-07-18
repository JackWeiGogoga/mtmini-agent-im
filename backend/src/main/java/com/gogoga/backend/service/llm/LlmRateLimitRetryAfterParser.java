package com.gogoga.backend.service.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 解析 HTTP Retry-After，兼容秒数和 RFC 1123 日期。
 */
@Component
public class LlmRateLimitRetryAfterParser {

    public long parseMillis(String value, Duration fallback) {
        long fallbackMillis = Math.max(fallback == null ? 0L : fallback.toMillis(), 1L);
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            return fallbackMillis;
        }
        try {
            return Math.max(Duration.ofSeconds(Long.parseLong(normalized)).toMillis(), 1L);
        } catch (RuntimeException ignored) {
            // 继续尝试 HTTP-date。
        }
        try {
            long millis = Duration.between(
                    ZonedDateTime.now(),
                    ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME)
            ).toMillis();
            return Math.max(millis, 1L);
        } catch (RuntimeException ignored) {
            return fallbackMillis;
        }
    }
}
