package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtReplyKnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论回复知识文档 Mapper。
 */
@Mapper
public interface MtReplyKnowledgeDocumentMapper {

    MtReplyKnowledgeDocument selectById(@Param("id") Long id);

    MtReplyKnowledgeDocument selectByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId);

    List<MtReplyKnowledgeDocument> selectByShopId(@Param("shopId") Long shopId);

    int countAvailableByShopId(@Param("shopId") Long shopId);

    int insert(MtReplyKnowledgeDocument document);

    int updateParseStatus(
            @Param("id") Long id,
            @Param("parseStatus") String parseStatus,
            @Param("parseError") String parseError,
            @Param("chunkCount") Integer chunkCount,
            @Param("updateTime") LocalDateTime updateTime
    );

    int softDeleteByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId, @Param("updateTime") LocalDateTime updateTime);

    int softDeleteByShopId(@Param("shopId") Long shopId, @Param("updateTime") LocalDateTime updateTime);
}
