package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.enums.MtPromotionLogMarkdownStatusEnum;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.service.MtPromotionAdDataService;
import com.gogoga.backend.service.PromotionPowerConsumeService;
import com.gogoga.backend.util.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionLogAsyncTaskServiceTest {

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private PromotionLogMarkdownService promotionLogMarkdownService;

    @Mock
    private PromotionLogMessageBuilder promotionLogMessageBuilder;

    @Mock
    private MtPromotionAdDataService mtPromotionAdDataService;

    @Mock
    private PromotionPowerConsumeService promotionPowerConsumeService;

    @InjectMocks
    private PromotionLogAsyncTaskService promotionLogAsyncTaskService;

    @Test
    void shouldGenerateAndPersistMarkdownForSuccessfulRecord() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setId(1L);
        record.setShopId(99L);
        record.setSlotTime(LocalDateTime.of(2026, 4, 18, 12, 0));
        record.setExecuteStatus("SUCCESS");
        PromotionLogMarkdownResult markdownResult = new PromotionLogMarkdownResult();
        markdownResult.setMarkdownMessage("### 调价成功");
        markdownResult.setFallback(false);
        markdownResult.setPromptTokens(210L);
        markdownResult.setCompletionTokens(111L);
        markdownResult.setTotalTokens(321L);

        when(mtPromotionBidRecordMapper.selectById(1L)).thenReturn(record);
        when(promotionLogMarkdownService.generate(record)).thenReturn(markdownResult);

        promotionLogAsyncTaskService.generateAndPersistMarkdown(1L, "token-1");

        verify(mtPromotionAdDataService).ensureRecentAdDataBackfillBeforeLogGeneration(99L, "token-1");
        verify(promotionPowerConsumeService).consumeForLogGeneration(99L, record.getSlotTime(), 210L, 111L);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(mtPromotionBidRecordMapper).updateMarkdownAndDecisionDetail(
                eq(1L),
                eq("### 调价成功"),
                eq(MtPromotionLogMarkdownStatusEnum.READY.name()),
                detailCaptor.capture(),
                any()
        );
        PromotionDecisionDetail detail = JsonUtil.toBean(detailCaptor.getValue(), PromotionDecisionDetail.class);
        assertEquals("llm", detail.getLogMarkdownSource());
        assertEquals(210L, detail.getLogLlmPromptTokens());
        assertEquals(111L, detail.getLogLlmCompletionTokens());
        assertEquals(321L, detail.getLogLlmTotalTokens());
    }

    @Test
    void shouldPersistFallbackForFailedRecordWithoutCallingLlm() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setId(2L);
        record.setShopId(88L);
        record.setSlotTime(LocalDateTime.of(2026, 4, 18, 12, 10));
        record.setExecuteStatus("FAILED");

        when(mtPromotionBidRecordMapper.selectById(2L)).thenReturn(record);
        when(promotionLogMessageBuilder.buildFallbackMarkdown(record)).thenReturn("执行失败 fallback");

        promotionLogAsyncTaskService.generateAndPersistMarkdown(2L, "token-2");

        verify(promotionLogMarkdownService, never()).generate(any());
        verify(promotionPowerConsumeService, never()).consumeForLogGeneration(any(), any(), any(), any());
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(mtPromotionBidRecordMapper).updateMarkdownAndDecisionDetail(
                eq(2L),
                eq("执行失败 fallback"),
                eq(MtPromotionLogMarkdownStatusEnum.FALLBACK.name()),
                detailCaptor.capture(),
                any()
        );
        PromotionDecisionDetail detail = JsonUtil.toBean(detailCaptor.getValue(), PromotionDecisionDetail.class);
        assertEquals("fallback", detail.getLogMarkdownSource());
        assertTrue(detail.getLogLlmFailureSummary().contains("NON_SUCCESS_RECORD_FALLBACK"));
    }
}
