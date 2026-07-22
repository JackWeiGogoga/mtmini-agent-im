package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.vo.ImBroadcastContentOptimizeVO;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImBroadcastContentServiceTest {

    @Mock private MtShopRightService shopRightService;
    @Mock private OpenAiCompatibleChatService chatService;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private MtPowerConsumeService powerConsumeService;
    @Mock private SystemPromptService systemPromptService;

    private MtProperties mtProperties;
    private ImBroadcastContentService service;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        service = new ImBroadcastContentService(mtProperties, new PowerProperties(), shopRightService,
                chatService, sensitiveWordService, powerConsumeService, systemPromptService);
        when(shopRightService.hasBroadcastRight(anyLong())).thenReturn(true);
        // 提示词渲染直接回传兜底内容，测试聚焦优化/计费/敏感词流程本身。
        when(systemPromptService.render(any(), any(), any())).thenAnswer(invocation -> {
            ResolvedSystemPromptDTO dto = new ResolvedSystemPromptDTO();
            dto.setContent(invocation.getArgument(2, String.class));
            return dto;
        });
    }

    @Test
    void optimizesContentBillsPowerAndReturnsResult() {
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(completion("宝子，快来下单吧😋", 30L, 20L));
        when(sensitiveWordService.detect(any())).thenReturn(noHit());

        ImBroadcastContentOptimizeVO vo = service.optimize(100L, "来下单");

        assertEquals("宝子，快来下单吧😋", vo.getContent());
        verify(chatService, times(1)).createChatCompletion(any(), any(), any(), any());
        ArgumentCaptor<PowerConsumeRequestDTO> captor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(powerConsumeService).consume(captor.capture());
        assertEquals(100L, captor.getValue().getShopId());
        assertTrue(captor.getValue().getPowerAmount() > 0);
    }

    @Test
    void rewritesOnceWhenFirstResultHitsSensitiveWord() {
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(completion("含敏感词", 30L, 20L))
                .thenReturn(completion("合规文案😊", 30L, 20L));
        when(sensitiveWordService.detect("含敏感词")).thenReturn(hit());
        when(sensitiveWordService.detect("合规文案😊")).thenReturn(noHit());

        ImBroadcastContentOptimizeVO vo = service.optimize(100L, "原始");

        assertEquals("合规文案😊", vo.getContent());
        // 命中后自动改写一次，两轮 token 合并计一次费。
        verify(chatService, times(2)).createChatCompletion(any(), any(), any(), any());
        verify(powerConsumeService, times(1)).consume(any());
    }

    @Test
    void throwsButStillBillsWhenRewriteStillHitsSensitiveWord() {
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(completion("敏感一", 30L, 20L))
                .thenReturn(completion("敏感二", 30L, 20L));
        when(sensitiveWordService.detect(any())).thenReturn(hit());

        assertThrows(BusinessException.class, () -> service.optimize(100L, "原始"));

        verify(chatService, times(2)).createChatCompletion(any(), any(), any(), any());
        // 模型成本已发生，即便最终不合规也要落账，不能白嫖。
        verify(powerConsumeService, times(1)).consume(any());
    }

    @Test
    void truncatesResultToMaxContentLength() {
        mtProperties.getImBroadcast().setMaxContentLength(10);
        when(chatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(completion("一二三四五六七八九十十十十", 30L, 20L));
        when(sensitiveWordService.detect(any())).thenReturn(noHit());

        ImBroadcastContentOptimizeVO vo = service.optimize(100L, "长文");

        assertEquals(10, vo.getContent().length());
    }

    @Test
    void rejectsWhenShopHasNoBroadcastRight() {
        when(shopRightService.hasBroadcastRight(100L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.optimize(100L, "内容"));

        verify(chatService, never()).createChatCompletion(any(), any(), any(), any());
        verify(powerConsumeService, never()).consume(any());
    }

    @Test
    void rejectsBlankContentWithoutCallingModel() {
        assertThrows(BusinessException.class, () -> service.optimize(100L, "   "));

        verify(chatService, never()).createChatCompletion(any(), any(), any(), any());
    }

    private SensitiveWordHitDTO hit() {
        SensitiveWordHitDTO dto = new SensitiveWordHitDTO();
        dto.setHit(true);
        return dto;
    }

    private SensitiveWordHitDTO noHit() {
        SensitiveWordHitDTO dto = new SensitiveWordHitDTO();
        dto.setHit(false);
        return dto;
    }

    private ChatCompletion completion(String content, long promptTokens, long completionTokens) {
        ChatCompletionMessage message = ChatCompletionMessage.builder()
                .content(content)
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
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
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
