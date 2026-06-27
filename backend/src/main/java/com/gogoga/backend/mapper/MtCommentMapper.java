package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentStatsAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 美团门店评价 Mapper。
 */
@Mapper
public interface MtCommentMapper {

    MtComment selectByCommentId(@Param("commentId") Long commentId);

    MtComment selectById(@Param("id") Long id);

    List<MtComment> selectPendingAutoReplyByShopId(@Param("shopId") Long shopId);

    List<MtComment> selectLogsByShopId(@Param("shopId") Long shopId);

    MtCommentStatsAggregate selectStatsByShopIdAndCreateTimeBetween(
            @Param("shopId") Long shopId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    int insert(MtComment comment);

    int updateFromQuery(MtComment comment);

    int markAutoReplySuccess(
            @Param("id") Long id,
            @Param("replyContent") String replyContent,
            @Param("autoReplyAt") LocalDateTime autoReplyAt,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markAutoReplySuccessWithGeneration(
            @Param("id") Long id,
            @Param("replyContent") String replyContent,
            @Param("autoReplyAt") LocalDateTime autoReplyAt,
            @Param("replySource") String replySource,
            @Param("replySentiment") String replySentiment,
            @Param("generationId") Long generationId,
            @Param("knowledgeHitJson") String knowledgeHitJson,
            @Param("replyModel") String replyModel,
            @Param("powerAmount") Integer powerAmount,
            @Param("durationMs") Integer durationMs,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markAutoReplyFailed(
            @Param("id") Long id,
            @Param("error") String error,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markAutoReplySkipped(
            @Param("id") Long id,
            @Param("error") String error,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markAnalysisSuccess(
            @Param("id") Long id,
            @Param("analysisText") String analysisText,
            @Param("analysisModel") String analysisModel,
            @Param("promptKey") String promptKey,
            @Param("promptVersion") Integer promptVersion,
            @Param("promptTokens") Integer promptTokens,
            @Param("completionTokens") Integer completionTokens,
            @Param("powerAmount") Integer powerAmount,
            @Param("durationMs") Integer durationMs,
            @Param("analysisAt") LocalDateTime analysisAt,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markAnalysisFailed(
            @Param("id") Long id,
            @Param("error") String error,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markComplaintGenerated(
            @Param("id") Long id,
            @Param("reportTypeCode") Integer reportTypeCode,
            @Param("reportContent") String reportContent,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markComplaintSuccess(
            @Param("id") Long id,
            @Param("reportTypeCode") Integer reportTypeCode,
            @Param("reportContent") String reportContent,
            @Param("complaintAt") LocalDateTime complaintAt,
            @Param("updateTime") LocalDateTime updateTime
    );

    int markComplaintFailed(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("error") String error,
            @Param("updateTime") LocalDateTime updateTime
    );
}
