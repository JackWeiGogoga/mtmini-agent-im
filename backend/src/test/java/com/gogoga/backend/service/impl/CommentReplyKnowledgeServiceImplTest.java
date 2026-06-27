package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtReplyKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtReplyKnowledgeDocumentMapper;
import com.gogoga.backend.mapper.MtReplyKnowledgeItemMapper;
import com.gogoga.backend.param.CommentReplyQaKnowledgeParam;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.service.CommentReplySensitiveWordService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtH5ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentReplyKnowledgeServiceImplTest {

    @Mock
    private MtH5ShopService mtH5ShopService;
    @Mock
    private MtReplyKnowledgeItemMapper mtReplyKnowledgeItemMapper;
    @Mock
    private MtReplyKnowledgeDocumentMapper mtReplyKnowledgeDocumentMapper;
    @Mock
    private MtReplyKnowledgeChunkMapper mtReplyKnowledgeChunkMapper;
    @Mock
    private CommentReplyEmbeddingService commentReplyEmbeddingService;
    @Mock
    private KnowledgeVectorStore knowledgeVectorStore;
    @Mock
    private CommentReplySensitiveWordService commentReplySensitiveWordService;

    private CommentReplyKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        service = new CommentReplyKnowledgeServiceImpl(
                mtH5ShopService,
                mtReplyKnowledgeItemMapper,
                mtReplyKnowledgeDocumentMapper,
                mtReplyKnowledgeChunkMapper,
                commentReplyEmbeddingService,
                knowledgeVectorStore,
                new MtProperties(),
                commentReplySensitiveWordService
        );
        lenient().doNothing().when(commentReplySensitiveWordService).validateKnowledgeText(any(), any(), any());
    }

    @Test
    void createCurrentShopQaRejectsWhenCountReachesLimit() {
        when(mtReplyKnowledgeItemMapper.countEnabledQaByShopId(100L)).thenReturn(500);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createCurrentShopQa(param(true)));

        assertEquals("每个商户最多上传 500 条问答知识", exception.getMessage());
        verify(mtReplyKnowledgeItemMapper, never()).insert(any());
    }

    @Test
    void createCurrentShopQaBuildsChunkWhenEnabled() {
        when(mtReplyKnowledgeItemMapper.countEnabledQaByShopId(100L)).thenReturn(0);
        doAnswer(invocation -> {
            MtReplyKnowledgeItem item = invocation.getArgument(0);
            item.setId(9L);
            return 1;
        }).when(mtReplyKnowledgeItemMapper).insert(any(MtReplyKnowledgeItem.class));
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        service.createCurrentShopQa(param(true));

        ArgumentCaptor<MtReplyKnowledgeChunk> chunkCaptor = ArgumentCaptor.forClass(MtReplyKnowledgeChunk.class);
        verify(knowledgeVectorStore).deleteBySource("QA", 9L);
        verify(knowledgeVectorStore).upsertChunk(chunkCaptor.capture());
        assertEquals(100L, chunkCaptor.getValue().getShopId());
        assertEquals("GOOD", chunkCaptor.getValue().getSentiment());
    }

    @Test
    void createCurrentShopQaRejectsSensitiveWordsBeforeInsert() {
        when(mtReplyKnowledgeItemMapper.countEnabledQaByShopId(100L)).thenReturn(0);
        doThrow(new BusinessException("内容命中敏感词类别：站外导流违规词"))
                .when(commentReplySensitiveWordService).validateKnowledgeText(eq(100L), eq("QA"), any());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createCurrentShopQa(param(true)));

        assertEquals("内容命中敏感词类别：站外导流违规词", exception.getMessage());
        verify(mtReplyKnowledgeItemMapper, never()).insert(any());
    }

    @Test
    void updateAndDeleteCurrentShopQaEnforceShopOwnership() {
        MtReplyKnowledgeItem otherShopItem = new MtReplyKnowledgeItem();
        otherShopItem.setId(8L);
        otherShopItem.setShopId(200L);
        otherShopItem.setSourceType("QA");
        otherShopItem.setDeleted(false);
        when(mtReplyKnowledgeItemMapper.selectById(8L)).thenReturn(otherShopItem);

        assertThrows(BusinessException.class, () -> service.updateCurrentShopQa(8L, param(true)));
        assertThrows(BusinessException.class, () -> service.deleteCurrentShopQa(8L));
    }

    @Test
    void disabledQaDeletesOldChunkWithoutRebuilding() {
        MtReplyKnowledgeItem item = new MtReplyKnowledgeItem();
        item.setId(8L);
        item.setShopId(100L);
        item.setSourceType("QA");
        item.setDeleted(false);
        when(mtReplyKnowledgeItemMapper.selectById(8L)).thenReturn(item);

        service.updateCurrentShopQa(8L, param(false));

        verify(knowledgeVectorStore).deleteBySource("QA", 8L);
        verify(knowledgeVectorStore, never()).upsertChunk(any());
    }

    @Test
    void clearCurrentShopKnowledgeSoftDeletesShopKnowledgeOnly() {
        service.clearCurrentShopKnowledge();

        verify(mtReplyKnowledgeItemMapper).softDeleteByShopId(100L);
        verify(mtReplyKnowledgeDocumentMapper).softDeleteByShopId(eq(100L), any());
        verify(mtReplyKnowledgeChunkMapper).softDeleteByShopId(eq(100L), any());
    }

    private CommentReplyQaKnowledgeParam param(boolean enabled) {
        CommentReplyQaKnowledgeParam param = new CommentReplyQaKnowledgeParam();
        param.setTitle("配送慢");
        param.setSentiment("GOOD");
        param.setQuestion("很好吃");
        param.setAnswer("感谢认可");
        param.setEnabled(enabled);
        return param;
    }
}
