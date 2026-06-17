package com.gogoga.backend.service;

import com.gogoga.backend.client.OpenAiCompatibleClientFactory;
import com.gogoga.backend.config.OpenAiCompatibleProperties;
import com.gogoga.backend.exception.BusinessException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleChatIntegrationTest {

    @Test
    void shouldCallCompatibleChatCompletionsEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              \"id\":\"chatcmpl-test\",
                              \"object\":\"chat.completion\",
                              \"created\":1710000000,
                              \"model\":\"gpt-4o-mini\",
                              \"choices\":[{
                                \"index\":0,
                                \"finish_reason\":\"stop\",
                                \"message\":{
                                  \"role\":\"assistant\",
                                  \"content\":\"hello from mock\"
                                }
                              }],
                              \"usage\":{
                                \"prompt_tokens\":9,
                                \"completion_tokens\":4,
                                \"total_tokens\":13
                              }
                            }
                            """));

            OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties();
            OpenAiCompatibleChatService service = new OpenAiCompatibleChatService(properties, new OpenAiCompatibleClientFactory());
            String baseUrl = server.url("/").toString();
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage("hello")
                    .build();

            ChatCompletion completion = service.createChatCompletion(baseUrl, "test-key", params);

            RecordedRequest request = server.takeRequest();
            assertEquals("/v1/chat/completions", request.getPath());
            assertEquals("Bearer test-key", request.getHeader("Authorization"));
            assertNotNull(completion);
            assertEquals("gpt-4o-mini", completion.model());
            assertTrue(completion.choices().size() > 0);
        }
    }

    @Test
    void shouldWrapInvalidPayloadFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("not-json"));

            OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties();
            OpenAiCompatibleChatService service = new OpenAiCompatibleChatService(properties, new OpenAiCompatibleClientFactory());
            String baseUrl = server.url("/").toString();
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage("hello")
                    .build();

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.createChatCompletion(baseUrl, "test-key", params));

            assertEquals(502, exception.getCode());
        }
    }
}
