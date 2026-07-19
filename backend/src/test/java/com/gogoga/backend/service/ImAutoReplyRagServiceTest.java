package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.entity.mt.MtKnowledgeChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImAutoReplyRagServiceTest {

    @Mock private KnowledgeVectorStore vectorStore;
    @Mock private KnowledgeEmbeddingService embeddingService;

    @Test
    void skipsEmbeddingWhenShopHasNoKnowledge() {
        when(vectorStore.hasShopKnowledge(1L)).thenReturn(false);

        ImAutoReplyRagService.RagContext result = service().retrieve(1L, List.of("配送多久"));

        assertFalse(result.used());
        verify(embeddingService, never()).embed("配送多久");
    }

    @Test
    void embedsRecentQuestionsAndBuildsIsolatedKnowledgeContext() {
        when(vectorStore.hasShopKnowledge(1L)).thenReturn(true);
        float[] embedding = new float[]{1F, 2F};
        when(embeddingService.embed("配送多久\n营业到几点")).thenReturn(embedding);
        KnowledgeSearchHitDTO hit = new KnowledgeSearchHitDTO();
        MtKnowledgeChunk chunk = new MtKnowledgeChunk();
        chunk.setChunkText("门店营业到 22:00");
        hit.setChunk(chunk);
        hit.setCosineScore(0.91D);
        when(vectorStore.searchTopK(1L, embedding)).thenReturn(List.of(hit));

        ImAutoReplyRagService.RagContext result = service().retrieve(
                1L, List.of("配送多久", "营业到几点"));

        assertTrue(result.used());
        assertTrue(result.promptContext().contains("门店营业到 22:00"));
        verify(vectorStore).searchTopK(1L, embedding);
    }

    private ImAutoReplyRagService service() {
        return new ImAutoReplyRagService(new MtProperties(), vectorStore, embeddingService);
    }
}
