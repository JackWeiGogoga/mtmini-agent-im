package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.enums.CommentReplyKnowledgeSourceTypeEnum;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.mapper.MtReplyKnowledgeChunkMapper;
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
    private MtReplyKnowledgeChunkMapper mtReplyKnowledgeChunkMapper;

    @Test
    void searchTopKSortsByCosineSentimentAndQaBonus() {
        MtProperties properties = new MtProperties();
        properties.getComment().getKnowledge().setTopK(2);
        properties.getComment().getKnowledge().setMaxSearchCandidates(10);
        MysqlKnowledgeVectorStore store = new MysqlKnowledgeVectorStore(mtReplyKnowledgeChunkMapper, properties);

        MtReplyKnowledgeChunk qaSameSentiment = chunk(1L, CommentReplyKnowledgeSourceTypeEnum.QA.name(), "GOOD", new float[]{0.95F, 0F});
        MtReplyKnowledgeChunk docUnknown = chunk(2L, CommentReplyKnowledgeSourceTypeEnum.DOCUMENT.name(), "UNKNOWN", new float[]{1F, 0F});
        MtReplyKnowledgeChunk docSameSentiment = chunk(3L, CommentReplyKnowledgeSourceTypeEnum.DOCUMENT.name(), "GOOD", new float[]{0.96F, 0F});
        when(mtReplyKnowledgeChunkMapper.selectSearchCandidates(
                100L,
                "GOOD",
                properties.getComment().getKnowledge().getEmbeddingProvider(),
                properties.getComment().getKnowledge().getEmbeddingModel(),
                properties.getComment().getKnowledge().getEmbeddingDim(),
                10
        )).thenReturn(List.of(docUnknown, docSameSentiment, qaSameSentiment));

        List<KnowledgeSearchHitDTO> hits = store.searchTopK(100L, CommentReplySentimentEnum.GOOD, new float[]{1F, 0F});

        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).getChunk().getId());
        assertEquals(3L, hits.get(1).getChunk().getId());
    }

    private MtReplyKnowledgeChunk chunk(Long id, String sourceType, String sentiment, float[] embedding) {
        MtReplyKnowledgeChunk chunk = new MtReplyKnowledgeChunk();
        chunk.setId(id);
        chunk.setSourceType(sourceType);
        chunk.setSourceId(id);
        chunk.setSentiment(sentiment);
        chunk.setChunkText("chunk-" + id);
        chunk.setEmbeddingBlob(EmbeddingVectorUtil.toFloat32Blob(embedding));
        return chunk;
    }
}
