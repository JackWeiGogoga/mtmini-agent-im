package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.ImAutoReplyChatMessage;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImAutoReplyChatServiceTest {

    @Mock private OpenAiCompatibleChatService compatibleChatService;

    @Test
    void parsesReplyAndActualUsageFromCompatibleChatResponse() {
        MtProperties properties = new MtProperties();
        properties.getImAutoReply().setModelApiKey("key");
        when(compatibleChatService.createChatCompletion(any(), any(), any(), any())).thenReturn(completion());
        ImAutoReplyChatService service = new ImAutoReplyChatService(properties, compatibleChatService);

        ImAutoReplyChatResult result = service.generate(
                "系统", "知识", List.of(new ImAutoReplyChatMessage("user", "你好")));

        assertEquals("您好", result.content());
        assertEquals(12, result.promptTokens());
        assertEquals(3, result.completionTokens());
        verify(compatibleChatService).createChatCompletion(
                eq("TOKENHUB"), any(), eq("key"), any());
    }

    private ChatCompletion completion() {
        ChatCompletionMessage message = ChatCompletionMessage.builder()
                .content("您好")
                .refusal(Optional.empty())
                .role(JsonValue.from("assistant"))
                .build();
        ChatCompletion.Choice choice = ChatCompletion.Choice.builder()
                .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                .index(0L)
                .logprobs(Optional.empty())
                .message(message)
                .build();
        CompletionUsage usage = CompletionUsage.builder()
                .promptTokens(12L)
                .completionTokens(3L)
                .totalTokens(15L)
                .build();
        return ChatCompletion.builder()
                .id("chat-1")
                .choices(List.of(choice))
                .created(1L)
                .model("test")
                .object_(JsonValue.from("chat.completion"))
                .usage(usage)
                .build();
    }
}
