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

    /**
     * 召回诊断：返回匹配当前 embedding 配置的候选片段（不做相似度阈值过滤），按原始余弦倒序取前 topN，
     * 供测试聊天排查“为何未命中”（阈值差多少 / 配置是否不匹配导致零候选）。
     */
    List<KnowledgeSearchHitDTO> diagnoseCandidates(Long shopId, float[] queryEmbedding, int topN);

    boolean hasShopKnowledge(Long shopId);
}
