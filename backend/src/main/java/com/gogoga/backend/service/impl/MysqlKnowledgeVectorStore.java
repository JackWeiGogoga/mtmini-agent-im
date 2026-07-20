package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.entity.mt.MtKnowledgeChunk;
import com.gogoga.backend.enums.KnowledgeSourceTypeEnum;
import com.gogoga.backend.mapper.MtKnowledgeChunkMapper;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.util.EmbeddingVectorUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL 候选召回 + Java cosine 排序的知识向量存储。
 */
@Service
@RequiredArgsConstructor
public class MysqlKnowledgeVectorStore implements KnowledgeVectorStore {

    private static final double QA_SOURCE_BONUS = 0.03D;
    private static final double CATEGORY_MATCH_BONUS = 0.05D;

    private final MtKnowledgeChunkMapper mtKnowledgeChunkMapper;
    private final MtProperties mtProperties;

    @Override
    public void upsertChunk(MtKnowledgeChunk chunk) {
        if (chunk == null) {
            return;
        }
        mtKnowledgeChunkMapper.insert(chunk);
    }

    @Override
    public void upsertChunks(List<MtKnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        mtKnowledgeChunkMapper.insertBatch(chunks);
    }

    @Override
    public void deleteBySource(String sourceType, Long sourceId) {
        if (StringUtils.isBlank(sourceType) || sourceId == null) {
            return;
        }
        mtKnowledgeChunkMapper.disableBySource(sourceType, sourceId, LocalDateTime.now());
    }

    @Override
    public List<KnowledgeSearchHitDTO> searchTopK(Long shopId, float[] queryEmbedding) {
        return searchTopK(shopId, queryEmbedding, null);
    }

    @Override
    public List<KnowledgeSearchHitDTO> searchTopK(Long shopId, float[] queryEmbedding, String preferredCategory) {
        if (shopId == null || queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }
        MtProperties.Knowledge knowledge = mtProperties.getKnowledge();
        int limit = Math.max(1, knowledge.getMaxSearchCandidates());
        int topK = Math.max(1, knowledge.getTopK());
        String category = StringUtils.trimToNull(preferredCategory);
        List<MtKnowledgeChunk> candidates = mtKnowledgeChunkMapper.selectSearchCandidates(
                shopId,
                knowledge.getEmbeddingProvider(),
                knowledge.getEmbeddingModel(),
                knowledge.getEmbeddingDim(),
                limit
        );
        return candidates.stream()
                .map(chunk -> toHit(chunk, queryEmbedding, category))
                .filter(hit -> hit.getCosineScore() >= knowledge.getMinContextCosineScore())
                .sorted(Comparator.comparing(KnowledgeSearchHitDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeSearchHitDTO> diagnoseCandidates(Long shopId, float[] queryEmbedding, int topN) {
        if (shopId == null || queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }
        MtProperties.Knowledge knowledge = mtProperties.getKnowledge();
        int limit = Math.max(1, knowledge.getMaxSearchCandidates());
        List<MtKnowledgeChunk> candidates = mtKnowledgeChunkMapper.selectSearchCandidates(
                shopId,
                knowledge.getEmbeddingProvider(),
                knowledge.getEmbeddingModel(),
                knowledge.getEmbeddingDim(),
                limit
        );
        return candidates.stream()
                .map(chunk -> toHit(chunk, queryEmbedding, null))
                .sorted(Comparator.comparing(KnowledgeSearchHitDTO::getCosineScore).reversed())
                .limit(Math.max(1, topN))
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasShopKnowledge(Long shopId) {
        return shopId != null && mtKnowledgeChunkMapper.countAvailableByShopId(shopId) > 0;
    }

    private KnowledgeSearchHitDTO toHit(MtKnowledgeChunk chunk, float[] queryEmbedding, String preferredCategory) {
        float[] embedding = EmbeddingVectorUtil.fromFloat32Blob(chunk.getEmbeddingBlob());
        double cosine = EmbeddingVectorUtil.cosineSimilarity(queryEmbedding, embedding);
        double score = cosine;
        if (StringUtils.equals(chunk.getSourceType(), KnowledgeSourceTypeEnum.QA.name())) {
            score += QA_SOURCE_BONUS;
        }
        if (preferredCategory != null && StringUtils.equalsIgnoreCase(chunk.getCategory(), preferredCategory)) {
            score += CATEGORY_MATCH_BONUS;
        }
        KnowledgeSearchHitDTO hit = new KnowledgeSearchHitDTO();
        hit.setChunk(chunk);
        hit.setCosineScore(cosine);
        hit.setScore(score);
        return hit;
    }
}
