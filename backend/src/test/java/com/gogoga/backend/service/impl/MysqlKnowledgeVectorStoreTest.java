package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.entity.mt.MtKnowledgeChunk;
import com.gogoga.backend.enums.KnowledgeSourceTypeEnum;
import com.gogoga.backend.mapper.MtKnowledgeChunkMapper;
import com.gogoga.backend.util.EmbeddingVectorUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MysqlKnowledgeVectorStoreTest {

    @Mock
    private MtKnowledgeChunkMapper chunkMapper;

    @Test
    void searchTopKAppliesThresholdTopKAndQaBonus() {
        MtProperties properties = new MtProperties();
        properties.getKnowledge().setTopK(2);
        properties.getKnowledge().setMaxSearchCandidates(10);
        properties.getKnowledge().setMinContextCosineScore(0.5D);
        MysqlKnowledgeVectorStore store = new MysqlKnowledgeVectorStore(chunkMapper, properties);

        MtKnowledgeChunk qa = chunk(1L, KnowledgeSourceTypeEnum.QA.name(), new float[]{0.95F, 0F});
        MtKnowledgeChunk doc = chunk(2L, KnowledgeSourceTypeEnum.DOCUMENT.name(), new float[]{1F, 0F});
        MtKnowledgeChunk belowThreshold = chunk(3L, KnowledgeSourceTypeEnum.DOCUMENT.name(), new float[]{0F, 1F});
        when(chunkMapper.selectSearchCandidates(
                100L,
                properties.getKnowledge().getEmbeddingProvider(),
                properties.getKnowledge().getEmbeddingModel(),
                properties.getKnowledge().getEmbeddingDim(),
                10
        )).thenReturn(List.of(doc, belowThreshold, qa));

        List<KnowledgeSearchHitDTO> hits = store.searchTopK(100L, new float[]{1F, 0F});

        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).getChunk().getId());
        assertEquals(2L, hits.get(1).getChunk().getId());
    }

    private MtKnowledgeChunk chunk(Long id, String sourceType, float[] embedding) {
        MtKnowledgeChunk chunk = new MtKnowledgeChunk();
        chunk.setId(id);
        chunk.setSourceType(sourceType);
        chunk.setSourceId(id);
        chunk.setChunkText("chunk-" + id);
        chunk.setEmbeddingBlob(EmbeddingVectorUtil.toFloat32Blob(embedding));
        return chunk;
    }
}
