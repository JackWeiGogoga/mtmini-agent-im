package com.gogoga.backend.service.llm;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmRateLimitRetryAfterParserTest {

    private final LlmRateLimitRetryAfterParser parser = new LlmRateLimitRetryAfterParser();

    @Test
    void shouldParseSeconds() {
        assertEquals(3000L, parser.parseMillis("3", Duration.ofSeconds(60)));
    }

    @Test
    void shouldUseFallbackForInvalidValue() {
        assertEquals(60000L, parser.parseMillis("invalid", Duration.ofSeconds(60)));
    }
}
