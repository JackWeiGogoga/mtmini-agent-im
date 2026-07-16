package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtCommentComplaint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 评价差评自动投诉 Mapper。
 */
@Mapper
public interface MtCommentComplaintMapper {

    MtCommentComplaint selectByShopIdAndCommentId(@Param("shopId") Long shopId, @Param("commentId") Long commentId);

    int insert(MtCommentComplaint complaint);

    int markGenerated(MtCommentComplaint complaint);

    int markSuccess(
            @Param("id") Long id,
            @Param("requestPayloadJson") String requestPayloadJson,
            @Param("responsePayloadJson") String responsePayloadJson,
            @Param("submittedAt") LocalDateTime submittedAt,
            @Param("durationMs") Integer durationMs,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markFailed(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("durationMs") Integer durationMs,
            @Param("updateTime") LocalDateTime updateTime
    );

    int resetFailedForManualRetry(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("updateTime") LocalDateTime updateTime
    );
}
