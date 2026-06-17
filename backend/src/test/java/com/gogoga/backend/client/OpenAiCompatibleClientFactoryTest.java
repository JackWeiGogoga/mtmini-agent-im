package com.gogoga.backend.client;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpenAiCompatibleClientFactoryTest {

    private final OpenAiCompatibleClientFactory factory = new OpenAiCompatibleClientFactory();

    @Test
    void shouldNormalizeBaseUrlToV1Root() {
        assertEquals("https://api.openai.com/v1", factory.normalizeBaseUrl("https://api.openai.com"));
        assertEquals("https://example.com/v1", factory.normalizeBaseUrl("https://example.com/"));
        assertEquals("https://example.com/v1", factory.normalizeBaseUrl("https://example.com/v1"));
        assertEquals("https://example.com/v1", factory.normalizeBaseUrl("https://example.com/v1/chat/completions"));
    }

    @Test
    void shouldReuseClientForSameBaseUrlAndApiKey() {
        OpenAIClient first = factory.getClient("https://example.com", "key-001");
        OpenAIClient second = factory.getClient("https://example.com/", "key-001");

        assertSame(first, second);
    }
}
