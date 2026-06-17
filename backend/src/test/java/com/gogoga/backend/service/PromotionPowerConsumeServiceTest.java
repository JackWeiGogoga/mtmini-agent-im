package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.service.promotion.PromotionDecisionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PromotionPowerConsumeServiceTest {

    @Mock
    private MtPowerConsumeService mtPowerConsumeService;

    private PromotionPowerConsumeService promotionPowerConsumeService;

    @BeforeEach
    void setUp() {
        promotionPowerConsumeService = new PromotionPowerConsumeService(mtPowerConsumeService, new PowerProperties());
    }

    @Test
    void shouldSkipPowerConsumeForRuleDecision() {
        PromotionDecisionResult result = new PromotionDecisionResult();
        result.setPowerConsumeMode(PromotionPowerConsumeService.POWER_MODE_RULE);

        promotionPowerConsumeService.consumeForDecision(1L, LocalDateTime.of(2026, 4, 9, 12, 30), result);

        verify(mtPowerConsumeService, never()).consume(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldConsumeByLlmTokens() {
        PromotionDecisionResult result = new PromotionDecisionResult();
        result.setPowerConsumeMode(PromotionPowerConsumeService.POWER_MODE_LLM);
        result.setLlmPromptTokens(200L);
        result.setLlmCompletionTokens(100L);

        promotionPowerConsumeService.consumeForDecision(9L, LocalDateTime.of(2026, 4, 9, 9, 0), result);

        ArgumentCaptor<PowerConsumeRequestDTO> requestCaptor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(mtPowerConsumeService).consume(requestCaptor.capture());
        PowerConsumeRequestDTO request = requestCaptor.getValue();
        assertEquals(PromotionPowerConsumeService.CONSUME_SOURCE_LLM, request.getSourceType());
        assertEquals(350, request.getPowerAmount());
        assertEquals("推广费用 LLM 计算算力消耗，promptTokens=200, completionTokens=100, totalTokens=300", request.getRemark());
    }

    @Test
    void shouldRejectInvalidTokenUsageForLlmMode() {
        assertThrows(IllegalArgumentException.class, () -> promotionPowerConsumeService.calculateLlmPowerAmount(0L, 0L));
    }

    @Test
    void shouldConsumeByLogGenerationTokens() {
        promotionPowerConsumeService.consumeForLogGeneration(7L, LocalDateTime.of(2026, 4, 9, 9, 0), 1200L, 400L);

        ArgumentCaptor<PowerConsumeRequestDTO> requestCaptor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(mtPowerConsumeService).consume(requestCaptor.capture());
        PowerConsumeRequestDTO request = requestCaptor.getValue();
        assertEquals(PromotionPowerConsumeService.CONSUME_SOURCE_LOG_LLM, request.getSourceType());
        assertEquals(1800, request.getPowerAmount());
        assertEquals("推广日志 LLM 生成算力消耗，promptTokens=1200, completionTokens=400, totalTokens=1600", request.getRemark());
    }
}
