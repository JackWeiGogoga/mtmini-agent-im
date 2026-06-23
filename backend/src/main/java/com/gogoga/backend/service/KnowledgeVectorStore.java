package com.gogoga.backend.service;

import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.enums.CommentReplySentimentEnum;

import java.util.List;

public interface KnowledgeVectorStore {

    void upsertChunk(MtReplyKnowledgeChunk chunk);

    void upsertChunks(List<MtReplyKnowledgeChunk> chunks);

    void deleteBySource(String sourceType, Long sourceId);

    List<KnowledgeSearchHitDTO> searchTopK(Long shopId, CommentReplySentimentEnum sentiment, float[] queryEmbedding);

    boolean hasShopKnowledge(Long shopId);
}
