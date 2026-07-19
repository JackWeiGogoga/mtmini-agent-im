package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtKnowledgeDocument;
import com.gogoga.backend.enums.KnowledgeDocumentParseStatusEnum;
import com.gogoga.backend.mapper.MtKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtKnowledgeDocumentMapper;
import com.gogoga.backend.service.CosStorageService;
import com.gogoga.backend.service.KnowledgeEmbeddingService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {

    @Mock private MtH5ShopService h5ShopService;
    @Mock private MtKnowledgeDocumentMapper documentMapper;
    @Mock private MtKnowledgeChunkMapper chunkMapper;
    @Mock private CosStorageService cosStorageService;
    @Mock private MtAsyncTaskService asyncTaskService;
    @Mock private KnowledgeVectorStore vectorStore;
    @Mock private KnowledgeEmbeddingService embeddingService;

    private KnowledgeDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocumentServiceImpl(
                h5ShopService, documentMapper, chunkMapper, cosStorageService,
                asyncTaskService, new MtProperties(), vectorStore, embeddingService);
    }

    @Test
    void repeatedParseReplacesAvailableChunksAndEndsSuccessful() {
        MtKnowledgeDocument document = document();
        when(documentMapper.selectByIdAndShopId(20L, 10L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/10/a.txt"))
                .thenReturn(CosStorageService.StoredObjectContent.builder()
                        .content("门店营业时间为上午九点。".getBytes(StandardCharsets.UTF_8))
                        .build());
        when(embeddingService.embed(any())).thenReturn(new float[]{1F, 0F});
        MtKnowledgeDocumentParseTaskPayload payload = payload();

        service.executeDocumentParseTask(payload);
        service.executeDocumentParseTask(payload);

        verify(vectorStore, times(2)).deleteBySource("DOCUMENT", 20L);
        verify(vectorStore, times(2)).upsertChunks(any());
        verify(documentMapper, times(2)).updateParseStatus(
                eq(20L), eq(KnowledgeDocumentParseStatusEnum.SUCCESS.name()), eq(null), eq(1), any());
    }

    @Test
    void parseFailureIsIsolatedToDocumentAndRethrownForWorkerRetry() {
        MtKnowledgeDocument document = document();
        when(documentMapper.selectByIdAndShopId(20L, 10L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/10/a.txt"))
                .thenThrow(new IllegalStateException("provider secret"));

        assertThrows(IllegalStateException.class, () -> service.executeDocumentParseTask(payload()));

        InOrder order = inOrder(documentMapper);
        order.verify(documentMapper).updateParseStatus(
                eq(20L), eq(KnowledgeDocumentParseStatusEnum.PARSING.name()), eq(null), eq(0), any());
        order.verify(documentMapper).updateParseStatus(
                eq(20L), eq(KnowledgeDocumentParseStatusEnum.FAILED.name()),
                eq("DOCUMENT_PARSE_FAILED:IllegalStateException"), eq(0), any());
    }

    private MtKnowledgeDocument document() {
        MtKnowledgeDocument document = new MtKnowledgeDocument();
        document.setId(20L);
        document.setShopId(10L);
        document.setFileType("TXT");
        document.setObjectKey("knowledge/10/a.txt");
        return document;
    }

    private MtKnowledgeDocumentParseTaskPayload payload() {
        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(10L);
        payload.setDocumentId(20L);
        return payload;
    }
}
