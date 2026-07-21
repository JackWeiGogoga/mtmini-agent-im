package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtKnowledgeItem;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtKnowledgeDocumentMapper;
import com.gogoga.backend.mapper.MtKnowledgeItemMapper;
import com.gogoga.backend.param.KnowledgeQaParam;
import com.gogoga.backend.service.KnowledgeEmbeddingService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.SensitiveWordService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImplTest {

    @Mock private MtH5ShopService h5ShopService;
    @Mock private MtKnowledgeItemMapper itemMapper;
    @Mock private MtKnowledgeDocumentMapper documentMapper;
    @Mock private MtKnowledgeChunkMapper chunkMapper;
    @Mock private KnowledgeEmbeddingService embeddingService;
    @Mock private KnowledgeVectorStore vectorStore;
    @Mock private SensitiveWordService sensitiveWordService;

    private KnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeServiceImpl(
                h5ShopService, itemMapper, documentMapper, chunkMapper,
                embeddingService, vectorStore, new MtProperties(), sensitiveWordService);
    }

    @Test
    void createsShopScopedQaAndItsGenericVectorChunk() {
        when(itemMapper.countEnabledQaByShopId(10L)).thenReturn(0);
        when(itemMapper.insert(any())).thenAnswer(invocation -> {
            ((MtKnowledgeItem) invocation.getArgument(0)).setId(77L);
            return 1;
        });
        when(embeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        service.createShopQa(10L, param(true));

        ArgumentCaptor<MtKnowledgeChunk> chunk = ArgumentCaptor.forClass(MtKnowledgeChunk.class);
        verify(vectorStore).upsertChunk(chunk.capture());
        assertEquals(10L, chunk.getValue().getShopId());
        assertEquals("QA", chunk.getValue().getSourceType());
        assertEquals(77L, chunk.getValue().getItemId());
    }

    @Test
    void rejectsCrossShopUpdateAndDelete() {
        MtKnowledgeItem item = new MtKnowledgeItem();
        item.setId(77L);
        item.setShopId(11L);
        when(itemMapper.selectById(77L)).thenReturn(item);

        assertThrows(BusinessException.class, () -> service.updateShopQa(10L, 77L, param(true)));
        assertThrows(BusinessException.class, () -> service.deleteShopQa(10L, 77L));

        verify(itemMapper, never()).updateById(any());
        verify(itemMapper, never()).softDeleteByIdAndShopId(any(), any());
    }

    @Test
    void updatesAndDeletesQaOnlyWithinItsOwningShop() {
        MtKnowledgeItem item = new MtKnowledgeItem();
        item.setId(77L);
        item.setShopId(10L);
        item.setEnabled(true);
        when(itemMapper.selectById(77L)).thenReturn(item);
        when(embeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        service.updateShopQa(10L, 77L, param(true));
        service.deleteShopQa(10L, 77L);

        verify(itemMapper).updateById(item);
        verify(itemMapper).softDeleteByIdAndShopId(77L, 10L);
        verify(vectorStore, times(2)).deleteBySource("QA", 77L);
    }

    @Test
    void disabledQaDoesNotRemainInVectorStore() {
        when(itemMapper.countEnabledQaByShopId(10L)).thenReturn(0);
        when(itemMapper.insert(any())).thenAnswer(invocation -> {
            ((MtKnowledgeItem) invocation.getArgument(0)).setId(77L);
            return 1;
        });

        service.createShopQa(10L, param(false));

        verify(vectorStore).deleteBySource("QA", 77L);
        verify(vectorStore, never()).upsertChunk(any());
    }

    @Test
    void rejectsSensitiveQaBeforeInsertAndEmbedding() {
        when(itemMapper.countEnabledQaByShopId(10L)).thenReturn(0);
        doThrow(new BusinessException("内容命中敏感词类别：站外导流违规词"))
                .when(sensitiveWordService).validateKnowledgeText(eq(10L), eq("QA"), any());

        assertThrows(BusinessException.class, () -> service.createShopQa(10L, param(true)));

        verify(itemMapper, never()).insert(any());
        verify(embeddingService, never()).embed(any());
    }

    private KnowledgeQaParam param(boolean enabled) {
        KnowledgeQaParam param = new KnowledgeQaParam();
        param.setTitle("营业时间");
        param.setQuestion("几点营业？");
        param.setAnswer("每天上午九点营业");
        param.setEnabled(enabled);
        return param;
    }
}
