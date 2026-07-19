package com.gogoga.backend.service;

import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.entity.mt.MtKnowledgeChunk;

import java.util.List;

public interface KnowledgeVectorStore {

    void upsertChunk(MtKnowledgeChunk chunk);

    void upsertChunks(List<MtKnowledgeChunk> chunks);

    void deleteBySource(String sourceType, Long sourceId);

    List<KnowledgeSearchHitDTO> searchTopK(Long shopId, float[] queryEmbedding);

    List<KnowledgeSearchHitDTO> searchTopK(Long shopId, float[] queryEmbedding, String preferredCategory);

    boolean hasShopKnowledge(Long shopId);
}
