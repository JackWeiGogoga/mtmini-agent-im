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

    List<MtComment> selectPendingAutoReplyByShopId(@Param("shopId") Long shopId);

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

    int markAutoReplyFailed(
            @Param("id") Long id,
            @Param("error") String error,
            @Param("updateTime") LocalDateTime updateTime
    );
}
