package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论回复知识片段 Mapper。
 */
@Mapper
public interface MtReplyKnowledgeChunkMapper {

    MtReplyKnowledgeChunk selectById(@Param("id") Long id);

    MtReplyKnowledgeChunk selectByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId);

    List<MtReplyKnowledgeChunk> selectByDocumentIdAndShopId(@Param("documentId") Long documentId, @Param("shopId") Long shopId);

    List<MtReplyKnowledgeChunk> selectSearchCandidates(
            @Param("shopId") Long shopId,
            @Param("sentiment") String sentiment,
            @Param("embeddingProvider") String embeddingProvider,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDim") Integer embeddingDim,
            @Param("limit") Integer limit
    );

    int countAvailableByShopId(@Param("shopId") Long shopId);

    int insert(MtReplyKnowledgeChunk chunk);

    int insertBatch(@Param("chunks") List<MtReplyKnowledgeChunk> chunks);

    int disableBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId, @Param("updateTime") LocalDateTime updateTime);

    int updateSentimentByIdAndShopId(
            @Param("id") Long id,
            @Param("shopId") Long shopId,
            @Param("sentiment") String sentiment,
            @Param("updateTime") LocalDateTime updateTime
    );

    int updateEnabledByIdAndShopId(
            @Param("id") Long id,
            @Param("shopId") Long shopId,
            @Param("enabled") Boolean enabled,
            @Param("updateTime") LocalDateTime updateTime
    );

    int softDeleteByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId, @Param("updateTime") LocalDateTime updateTime);
}
