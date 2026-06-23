package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtCommentImageSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 评价图片识别摘要 Mapper。
 */
@Mapper
public interface MtCommentImageSummaryMapper {

    MtCommentImageSummary selectByShopIdAndCommentId(@Param("shopId") Long shopId, @Param("commentId") Long commentId);

    int insert(MtCommentImageSummary summary);

    int updateResultById(MtCommentImageSummary summary);

    int updateFailureById(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("updateTime") LocalDateTime updateTime
    );
}
