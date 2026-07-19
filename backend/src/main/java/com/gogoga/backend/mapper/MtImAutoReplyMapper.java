package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImAutoReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MtImAutoReplyMapper {

    int insert(MtImAutoReply autoReply);

    MtImAutoReply selectById(@Param("id") Long id);

    MtImAutoReply selectByTriggerMessageId(@Param("triggerMessageId") Long triggerMessageId);

    int update(MtImAutoReply autoReply);

    /**
     * 时间段内已发送(SENT)的自动回复数（按 sent_at）。
     */
    int countSentBetween(@Param("shopId") Long shopId,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    /**
     * 时间段内售后类用户消息数（按 create_time，user_message_category=AFTER_SALE）。
     */
    int countAfterSaleBetween(@Param("shopId") Long shopId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    /**
     * 时间段内触发自动回复的记录总数（按 create_time），作为回复率分母。
     */
    int countTriggeredBetween(@Param("shopId") Long shopId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);
}
