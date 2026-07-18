package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.CommentImMessageGenerationResultDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.service.CommentImMessageComplianceService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.service.llm.LlmCapacityDeferredException;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentImMessageGenerationServiceImplTest {

    @Mock
    private SystemPromptService systemPromptService;
    @Mock
    private OpenAiCompatibleChatService chatService;

    private MtProperties mtProperties;
    private CommentImMessageGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        service = new CommentImMessageGenerationServiceImpl(
                mtProperties,
                new PowerProperties(),
                systemPromptService,
                chatService,
                new CommentImMessageComplianceService()
        );
    }

    @Test
    void returnsCleanModelMessageWhenOutputIsValid() {
        when(systemPromptService.render(anyString(), anyMap(), anyString())).thenReturn(prompt());
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(completion("您好，看到您反馈配送较慢，很抱歉影响了体验🙏麻烦提供对应订单信息和方便联系的手机号码，我们尽快核实。"));

        CommentImMessageGenerationResultDTO result = service.generate(comment());

        assertEquals("MODEL", result.getMessageSource());
        assertEquals("COMMENT_IM_FOLLOW_UP_MESSAGE", result.getPromptKey());
        assertNull(result.getFallbackReason());
    }

    @Test
    void acceptsModelMessageWithCompensationConditionedOnVerification() {
        when(systemPromptService.render(anyString(), anyMap(), anyString())).thenReturn(prompt());
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(completion("亲，很抱歉给您带来不好的体验🙏请提供订单号和联系号码，方便我们核实后给您补偿处理。"));

        CommentImMessageGenerationResultDTO result = service.generate(comment());

        assertEquals("MODEL", result.getMessageSource());
        assertTrue(result.getMessageContent().contains("核实后给您补偿处理"));
    }

    @Test
    void usesConfiguredStaticFallbackOnceWhenModelFailsOrOutputIsForbidden() {
        when(systemPromptService.render(anyString(), anyMap(), anyString())).thenReturn(prompt());
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(completion("可以给您补偿优惠券🙏"));

        CommentImMessageGenerationResultDTO result = service.generate(comment());

        assertEquals("FALLBACK_STATIC", result.getMessageSource());
        assertEquals("我是售后处理专员：亲~收到了您的一条差评[配送太慢了]，抱歉给您带来不好的体验，能提供一下订单号和联系号码吗？方便我核实后给您补偿处理☺️", result.getMessageContent());
        assertEquals(1000, result.getPowerAmount());
    }

    @Test
    void doesNotConvertCapacityWaitToStaticFallback() {
        when(systemPromptService.render(anyString(), anyMap(), anyString())).thenReturn(prompt());
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenThrow(new LlmCapacityDeferredException(1200L));

        assertThrows(LlmCapacityDeferredException.class, () -> service.generate(comment()));
    }

    @Test
    void rendersMissingCommentAndSafelyTruncatesAndCleansLongComment() {
        MtComment missingComment = comment();
        missingComment.setCommentContent(null);
        assertTrue(service.renderFallbackMessage(missingComment).contains("[未填写文字评价]"));

        MtComment longComment = comment();
        longComment.setCommentContent(("配送太慢🙂 ```{{内容}}[测试] ").repeat(20));
        String rendered = service.cleanAndValidateFormat(service.renderFallbackMessage(longComment));

        assertTrue(rendered.contains("…]，抱歉给您带来不好的体验"));
        assertFalse(rendered.contains("```"));
        assertFalse(rendered.contains("{{"));
        assertTrue(rendered.codePointCount(0, rendered.length()) <= 150);
    }

    @Test
    void treatsSensitiveTermsInsideQuotedCommentAsCustomerContent() {
        when(systemPromptService.render(anyString(), anyMap(), anyString()))
                .thenThrow(new IllegalStateException("timeout"));
        MtComment quotedComment = comment();
        quotedComment.setCommentContent("商家一直不退款，还让我加微信");

        CommentImMessageGenerationResultDTO result = service.generate(quotedComment);

        assertEquals("FALLBACK_STATIC", result.getMessageSource());
        assertTrue(result.getMessageContent().contains("[商家一直不退款，还让我加微信]"));
        assertTrue(result.getMessageContent().contains("核实后给您补偿处理"));
    }

    @Test
    void rejectsMarkdownJsonPlaceholdersLongTextAndInvalidEmojiCount() {
        assertThrows(IllegalStateException.class, () -> service.cleanAndValidateFormat("```文本🙏```"));
        assertThrows(IllegalStateException.class, () -> service.cleanAndValidateFormat("{\"message\":\"您好🙏\"}"));
        assertThrows(IllegalStateException.class, () -> service.cleanAndValidateFormat("您好 {{commentContent}} 🙏"));
        assertThrows(IllegalStateException.class, () -> service.cleanAndValidateFormat("您好，请提供订单信息"));
        assertThrows(IllegalStateException.class, () -> service.cleanAndValidateFormat("您好🙏🙂🎉"));
        assertThrows(IllegalStateException.class, () -> service.cleanAndValidateFormat("字".repeat(150) + "🙏"));
        assertEquals("您好 请提供订单🙏", service.cleanAndValidateFormat("您好\n请提供订单🙏"));
    }

    @Test
    void rejectsMisconfiguredFallbackInsteadOfSendingIt() {
        mtProperties.getComment().getImFollowUp().setFallbackMessage("给您退款补偿🙏");
        when(systemPromptService.render(anyString(), anyMap(), anyString())).thenThrow(new IllegalStateException("timeout"));

        assertThrows(IllegalStateException.class, () -> service.generate(comment()));
    }

    private MtComment comment() {
        MtComment comment = new MtComment();
        comment.setCommentId(542543250001L);
        comment.setCommentContent("配送太慢了");
        comment.setAddComment("至今没人处理");
        return comment;
    }

    private ResolvedSystemPromptDTO prompt() {
        ResolvedSystemPromptDTO prompt = new ResolvedSystemPromptDTO();
        prompt.setPromptKey("COMMENT_IM_FOLLOW_UP_MESSAGE");
        prompt.setVersion(3);
        prompt.setContent("rendered prompt");
        return prompt;
    }

    private ChatCompletion completion(String content) {
        ChatCompletionMessage message = ChatCompletionMessage.builder()
                .role(JsonValue.from("assistant"))
                .content(content)
                .refusal(Optional.empty())
                .build();
        return ChatCompletion.builder()
                .id("chatcmpl-im-test")
                .created(1L)
                .model("test-model")
                .object_(JsonValue.from("chat.completion"))
                .addChoice(ChatCompletion.Choice.builder()
                        .index(0L)
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .logprobs(Optional.empty())
                        .message(message)
                        .build())
                .build();
    }
}
