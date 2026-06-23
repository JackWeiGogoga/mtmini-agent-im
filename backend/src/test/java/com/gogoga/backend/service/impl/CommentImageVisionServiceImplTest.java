package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentImageSummary;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtCommentImageSummaryMapper;
import com.gogoga.backend.service.MtPowerConsumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentImageVisionServiceImplTest {

    @Mock
    private MtCommentImageSummaryMapper mtCommentImageSummaryMapper;
    @Mock
    private MtPowerConsumeService mtPowerConsumeService;

    private MtProperties mtProperties;
    private CommentImageVisionServiceImpl service;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        mtProperties.getComment().getKnowledge().setVisionModelApiKey("");
        service = new CommentImageVisionServiceImpl(
                mtProperties,
                new PowerProperties(),
                mtCommentImageSummaryMapper,
                mtPowerConsumeService
        );
    }

    @Test
    void simulatedSummaryDoesNotPersistOrCharge() {
        MtComment comment = comment();
        comment.setImageSummaryText("图片显示餐品包装完整。");

        CommentImageSummaryResultDTO result = service.resolveForReply(shop(), comment, true);

        assertEquals("SIMULATED", result.getStatus());
        assertEquals("图片显示餐品包装完整。", result.getSummaryText());
        verify(mtCommentImageSummaryMapper, never()).insert(any());
        verify(mtPowerConsumeService, never()).consume(any());
    }

    @Test
    void existingSuccessSummaryIsReusedWithoutChargingAgain() {
        MtCommentImageSummary existing = new MtCommentImageSummary();
        existing.setId(5L);
        existing.setShopId(100L);
        existing.setCommentId(542543250001L);
        existing.setStatus("SUCCESS");
        existing.setImageCount(1);
        existing.setSummaryText("图片显示包装破损。");
        existing.setPowerAmount(10);
        when(mtCommentImageSummaryMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(existing);

        CommentImageSummaryResultDTO result = service.resolveForReply(shop(), comment(), true);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("图片显示包装破损。", result.getSummaryText());
        assertEquals(10, result.getPowerAmount());
        verify(mtPowerConsumeService, never()).consume(any());
    }

    @Test
    void missingApiKeyStoresFailureAndReturnsFallbackSummary() {
        when(mtCommentImageSummaryMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            MtCommentImageSummary summary = invocation.getArgument(0);
            summary.setId(9L);
            return 1;
        }).when(mtCommentImageSummaryMapper).insert(any(MtCommentImageSummary.class));

        CommentImageSummaryResultDTO result = service.resolveForReply(shop(), comment(), true);

        assertEquals("FAILED", result.getStatus());
        assertEquals("VISION_MODEL_API_KEY_EMPTY", result.getErrorMessage());
        assertTrue(result.getSummaryText().contains("系统未能识别图片内容"));
        verify(mtCommentImageSummaryMapper).updateFailureById(eq(9L), eq("FAILED"), eq("VISION_MODEL_API_KEY_EMPTY"), any());
        verify(mtPowerConsumeService, never()).consume(any());
    }

    private MtShop shop() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        return shop;
    }

    private MtComment comment() {
        MtComment comment = new MtComment();
        comment.setId(1L);
        comment.setShopId(100L);
        comment.setCommentId(542543250001L);
        comment.setCommentPictures("https://example.com/1.jpg,https://example.com/2.jpg");
        return comment;
    }
}
