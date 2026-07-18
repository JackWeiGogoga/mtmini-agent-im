package com.gogoga.backend.service.impl;

import com.gogoga.backend.client.OpenAiCompatibleClientFactory;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.service.llm.LlmClusterRateLimiter;
import com.gogoga.backend.service.llm.LlmCapacityDeferredException;
import com.gogoga.backend.service.llm.LlmRateLimitRetryAfterParser;
import com.gogoga.backend.service.llm.LlmRateLimitedRestExecutor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OpenAiCompatibleEmbeddingServiceImplTest {

    @Test
    void callsDashScopeCompatibleEmbeddingEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "object": "list",
                              "model": "text-embedding-v4",
                              "data": [
                                {
                                  "object": "embedding",
                                  "index": 0,
                                  "embedding": [0.1, 0.2, 0.3]
                                }
                              ]
                            }
                            """));
            MtProperties properties = new MtProperties();
            properties.getComment().getKnowledge().setEmbeddingBaseUrl(server.url("/compatible-mode/v1").toString());
            properties.getComment().getKnowledge().setEmbeddingApiKey("dashscope-key");
            properties.getComment().getKnowledge().setEmbeddingDim(3);
            OpenAiCompatibleEmbeddingServiceImpl service = new OpenAiCompatibleEmbeddingServiceImpl(
                    properties,
                    new OpenAiCompatibleClientFactory(),
                    rateLimitedRestExecutor()
            );

            float[] embedding = service.embed("衣服的质量杠杠的");

            RecordedRequest request = server.takeRequest();
            assertEquals("/compatible-mode/v1/embeddings", request.getPath());
            assertEquals("Bearer dashscope-key", request.getHeader("Authorization"));
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"model\":\"text-embedding-v4\""));
            assertTrue(body.contains("\"input\":\"衣服的质量杠杠的\""));
            assertTrue(body.contains("\"dimensions\":3"));
            assertTrue(body.contains("\"encoding_format\":\"float\""));
            assertArrayEquals(new float[]{0.1F, 0.2F, 0.3F}, embedding, 0.000001F);
        }
    }

    @Test
    void rejectsBlankTextBeforeCallingProvider() {
        MtProperties properties = new MtProperties();
        properties.getComment().getKnowledge().setEmbeddingBaseUrl("https://example.com/compatible-mode/v1");
        properties.getComment().getKnowledge().setEmbeddingApiKey("dashscope-key");
        properties.getComment().getKnowledge().setEmbeddingDim(1024);
        OpenAiCompatibleEmbeddingServiceImpl service = new OpenAiCompatibleEmbeddingServiceImpl(
                properties,
                new OpenAiCompatibleClientFactory(),
                rateLimitedRestExecutor()
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.embed(" "));

        assertEquals(400, exception.getCode());
    }

    @Test
    void preservesCapacityDeferredException() {
        MtProperties properties = new MtProperties();
        properties.getComment().getKnowledge().setEmbeddingBaseUrl("https://example.com/compatible-mode/v1");
        properties.getComment().getKnowledge().setEmbeddingApiKey("dashscope-key");
        LlmRateLimitedRestExecutor executor = mock(LlmRateLimitedRestExecutor.class);
        when(executor.execute(any(), any())).thenThrow(new LlmCapacityDeferredException(1200L));
        OpenAiCompatibleEmbeddingServiceImpl service = new OpenAiCompatibleEmbeddingServiceImpl(
                properties,
                new OpenAiCompatibleClientFactory(),
                executor
        );

        assertThrows(LlmCapacityDeferredException.class, () -> service.embed("评价知识"));
    }

    private LlmRateLimitedRestExecutor rateLimitedRestExecutor() {
        return new LlmRateLimitedRestExecutor(
                mock(LlmClusterRateLimiter.class),
                new LlmRateLimitRetryAfterParser()
        );
    }
}
