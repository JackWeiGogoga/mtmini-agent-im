package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.service.async.MtCommentLogAnalysisTaskPayload;
import com.gogoga.backend.service.llm.LlmCapacityDeferredException;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentLogAnalysisServiceImplTest {

    @Mock
    private SystemPromptService systemPromptService;
    @Mock
    private OpenAiCompatibleChatService chatService;
    @Mock
    private MtPowerConsumeService powerConsumeService;
    @Mock
    private MtCommentMapper commentMapper;
    @Mock
    private MtShopMapper shopMapper;

    private CommentLogAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentLogAnalysisServiceImpl(
                new MtProperties(),
                new PowerProperties(),
                systemPromptService,
                chatService,
                powerConsumeService,
                commentMapper,
                shopMapper
        );
    }

    @Test
    void shouldExecuteIndependentTaskAndUseStablePowerRequestNumber() {
        MtComment comment = comment();
        when(commentMapper.selectByCommentId(comment.getCommentId())).thenReturn(comment);
        when(shopMapper.selectById(comment.getShopId())).thenReturn(shop());
        when(systemPromptService.render(anyString(), anyMap(), anyString())).thenReturn(prompt());
        when(chatService.createChatCompletion(any(), any(), any(), any())).thenReturn(completion("建议优化出餐和包装检查。"));

        service.executeCommentLogAnalysisTask(payload());

        ArgumentCaptor<PowerConsumeRequestDTO> captor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(powerConsumeService).consume(captor.capture());
        assertEquals("COMMENT_LOG_ANALYSIS:542543250001", captor.getValue().getRequestNo());
        verify(commentMapper).markAnalysisSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldSkipAlreadySuccessfulAnalysis() {
        MtComment comment = comment();
        comment.setAnalysisStatus("SUCCESS");
        when(commentMapper.selectByCommentId(comment.getCommentId())).thenReturn(comment);

        service.executeCommentLogAnalysisTask(payload());

        verify(shopMapper, never()).selectById(any());
        verify(chatService, never()).createChatCompletion(any(), any(), any(), any());
    }

    @Test
    void capacityWaitShouldNotMarkAnalysisFailed() {
        MtComment comment = comment();
        when(commentMapper.selectByCommentId(comment.getCommentId())).thenReturn(comment);
        when(shopMapper.selectById(comment.getShopId())).thenReturn(shop());
        when(systemPromptService.render(anyString(), anyMap(), anyString())).thenReturn(prompt());
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenThrow(new LlmCapacityDeferredException(1200L));

        assertThrows(LlmCapacityDeferredException.class,
                () -> service.executeCommentLogAnalysisTask(payload()));

        verify(commentMapper, never()).markAnalysisFailed(any(), any(), any());
        verify(powerConsumeService, never()).consume(any());
    }

    private MtCommentLogAnalysisTaskPayload payload() {
        MtCommentLogAnalysisTaskPayload payload = new MtCommentLogAnalysisTaskPayload();
        payload.setShopId(100L);
        payload.setCommentId(542543250001L);
        return payload;
    }

    private MtComment comment() {
        MtComment comment = new MtComment();
        comment.setId(1L);
        comment.setShopId(100L);
        comment.setCommentId(542543250001L);
        comment.setAutoReplyStatus("SUCCESS");
        comment.setCommentContent("配送慢，包装破损");
        return comment;
    }

    private MtShop shop() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        return shop;
    }

    private ResolvedSystemPromptDTO prompt() {
        ResolvedSystemPromptDTO prompt = new ResolvedSystemPromptDTO();
        prompt.setPromptKey("COMMENT_LOG_ANALYSIS");
        prompt.setVersion(1);
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
                .id("chatcmpl-analysis-test")
                .created(1L)
                .model("deepseek-v4-flash")
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
