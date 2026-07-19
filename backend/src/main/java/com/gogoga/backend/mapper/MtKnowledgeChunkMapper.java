package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtKnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店知识片段 Mapper。
 */
@Mapper
public interface MtKnowledgeChunkMapper {

    MtKnowledgeChunk selectById(@Param("id") Long id);

    MtKnowledgeChunk selectByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId);

    List<MtKnowledgeChunk> selectByDocumentIdAndShopId(@Param("documentId") Long documentId, @Param("shopId") Long shopId);

    List<MtKnowledgeChunk> selectSearchCandidates(
            @Param("shopId") Long shopId,
            @Param("embeddingProvider") String embeddingProvider,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDim") Integer embeddingDim,
            @Param("limit") Integer limit
    );

    int countAvailableByShopId(@Param("shopId") Long shopId);

    int insert(MtKnowledgeChunk chunk);

    int insertBatch(@Param("chunks") List<MtKnowledgeChunk> chunks);

    int disableBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId, @Param("updateTime") LocalDateTime updateTime);

    int updateEnabledByIdAndShopId(
            @Param("id") Long id,
            @Param("shopId") Long shopId,
            @Param("enabled") Boolean enabled,
            @Param("updateTime") LocalDateTime updateTime
    );

    int softDeleteByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId, @Param("updateTime") LocalDateTime updateTime);

    int softDeleteByShopId(@Param("shopId") Long shopId, @Param("updateTime") LocalDateTime updateTime);
}
