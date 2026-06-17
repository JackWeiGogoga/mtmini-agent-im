package com.gogoga.backend.service;

import com.gogoga.backend.client.OpenAiCompatibleClientFactory;
import com.gogoga.backend.config.OpenAiCompatibleProperties;
import com.gogoga.backend.exception.BusinessException;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleChatServiceTest {

    @Spy
    private OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties();

    @Mock
    private OpenAiCompatibleClientFactory clientFactory;

    @Mock
    private OpenAIClient client;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatCompletionService completionService;

    @InjectMocks
    private OpenAiCompatibleChatService service;

    @Test
    void shouldUseExplicitBaseUrlAndApiKey() {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("gpt-4o-mini")
                .addUserMessage("hello")
                .build();

        when(clientFactory.getClient("https://example.com", "key-001")).thenReturn(client);
        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn((ChatCompletion) null);

        ChatCompletion result = service.createChatCompletion("https://example.com", "key-001", params);

        assertNull(result);
    }

    @Test
    void shouldUseDefaultConfigWhenExplicitValuesMissing() {
        properties.setDefaultBaseUrl("https://default.example.com");
        properties.setDefaultApiKey("default-key");
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("gpt-4o-mini")
                .addUserMessage("hello")
                .build();

        when(clientFactory.getClient("https://default.example.com", "default-key")).thenReturn(client);
        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn((ChatCompletion) null);

        ChatCompletion result = service.createChatCompletion(params);

        assertNull(result);
    }

    @Test
    void shouldRejectWhenApiKeyMissing() {
        properties.setDefaultBaseUrl("https://default.example.com");
        properties.setDefaultApiKey(null);
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("gpt-4o-mini")
                .addUserMessage("hello")
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createChatCompletion(null, null, params));

        assertEquals(400, exception.getCode());
        assertEquals("OpenAI 兼容服务 apiKey 未配置", exception.getMessage());
    }

    @Test
    void shouldWrapProviderFailure() {
        properties.setDefaultBaseUrl("https://default.example.com");
        properties.setDefaultApiKey("default-key");
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("gpt-4o-mini")
                .addUserMessage("hello")
                .build();

        when(clientFactory.getClient("https://default.example.com", "default-key")).thenReturn(client);
        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenThrow(new RuntimeException("boom"));
        when(clientFactory.normalizeBaseUrl("https://default.example.com")).thenReturn("https://default.example.com/v1");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createChatCompletion(params));

        assertEquals(502, exception.getCode());
        assertEquals("调用 OpenAI 兼容聊天补全失败", exception.getMessage());
    }
}
