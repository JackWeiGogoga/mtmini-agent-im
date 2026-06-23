package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.enums.CommentReplyKnowledgeSourceTypeEnum;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.mapper.MtReplyKnowledgeChunkMapper;
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

    private static final double SENTIMENT_MATCH_BONUS = 0.05D;
    private static final double QA_SOURCE_BONUS = 0.03D;

    private final MtReplyKnowledgeChunkMapper mtReplyKnowledgeChunkMapper;
    private final MtProperties mtProperties;

    @Override
    public void upsertChunk(MtReplyKnowledgeChunk chunk) {
        if (chunk == null) {
            return;
        }
        mtReplyKnowledgeChunkMapper.insert(chunk);
    }

    @Override
    public void upsertChunks(List<MtReplyKnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        mtReplyKnowledgeChunkMapper.insertBatch(chunks);
    }

    @Override
    public void deleteBySource(String sourceType, Long sourceId) {
        if (StringUtils.isBlank(sourceType) || sourceId == null) {
            return;
        }
        mtReplyKnowledgeChunkMapper.disableBySource(sourceType, sourceId, LocalDateTime.now());
    }

    @Override
    public List<KnowledgeSearchHitDTO> searchTopK(Long shopId, CommentReplySentimentEnum sentiment, float[] queryEmbedding) {
        if (shopId == null || sentiment == null || sentiment == CommentReplySentimentEnum.UNKNOWN
                || queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        int limit = Math.max(1, knowledge.getMaxSearchCandidates());
        int topK = Math.max(1, knowledge.getTopK());
        List<MtReplyKnowledgeChunk> candidates = mtReplyKnowledgeChunkMapper.selectSearchCandidates(
                shopId,
                sentiment.name(),
                knowledge.getEmbeddingProvider(),
                knowledge.getEmbeddingModel(),
                knowledge.getEmbeddingDim(),
                limit
        );
        return candidates.stream()
                .map(chunk -> toHit(chunk, sentiment, queryEmbedding))
                .sorted(Comparator.comparing(KnowledgeSearchHitDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasShopKnowledge(Long shopId) {
        return shopId != null && mtReplyKnowledgeChunkMapper.countAvailableByShopId(shopId) > 0;
    }

    private KnowledgeSearchHitDTO toHit(MtReplyKnowledgeChunk chunk, CommentReplySentimentEnum sentiment, float[] queryEmbedding) {
        float[] embedding = EmbeddingVectorUtil.fromFloat32Blob(chunk.getEmbeddingBlob());
        double cosine = EmbeddingVectorUtil.cosineSimilarity(queryEmbedding, embedding);
        double score = cosine;
        if (StringUtils.equals(chunk.getSentiment(), sentiment.name())) {
            score += SENTIMENT_MATCH_BONUS;
        }
        if (StringUtils.equals(chunk.getSourceType(), CommentReplyKnowledgeSourceTypeEnum.QA.name())) {
            score += QA_SOURCE_BONUS;
        }
        KnowledgeSearchHitDTO hit = new KnowledgeSearchHitDTO();
        hit.setChunk(chunk);
        hit.setCosineScore(cosine);
        hit.setScore(score);
        return hit;
    }
}
