package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtCommentImFollowUp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 差评匿名群聊跟进 Mapper。
 */
@Mapper
public interface MtCommentImFollowUpMapper {

    MtCommentImFollowUp selectByShopIdAndCommentId(@Param("shopId") Long shopId, @Param("commentId") Long commentId);

    MtCommentImFollowUp selectByMtCommentId(@Param("mtCommentId") Long mtCommentId);

    List<MtCommentImFollowUp> selectByMtCommentIds(@Param("mtCommentIds") List<Long> mtCommentIds);

    int insert(MtCommentImFollowUp record);

    int markGenerated(MtCommentImFollowUp record);

    int claimCreateGroupAttempt(@Param("id") Long id, @Param("attemptedAt") LocalDateTime attemptedAt,
                                @Param("updateTime") LocalDateTime updateTime);

    int markGroupCreated(MtCommentImFollowUp record);

    int initializeMessageIdentity(@Param("id") Long id, @Param("messageId") Long messageId,
                                  @Param("messageCts") Long messageCts,
                                  @Param("messageContentHash") String messageContentHash,
                                  @Param("updateTime") LocalDateTime updateTime);

    int claimSendAttempt(@Param("id") Long id, @Param("attemptedAt") LocalDateTime attemptedAt,
                         @Param("updateTime") LocalDateTime updateTime);

    int markSuccess(MtCommentImFollowUp record);

    int markTerminal(@Param("id") Long id, @Param("status") String status,
                     @Param("failedStage") String failedStage, @Param("errorCode") String errorCode,
                     @Param("errorMessage") String errorMessage, @Param("durationMs") Integer durationMs,
                     @Param("updateTime") LocalDateTime updateTime);
}
