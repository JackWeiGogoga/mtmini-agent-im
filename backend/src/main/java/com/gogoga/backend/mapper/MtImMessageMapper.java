package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MtImMessageMapper {

    int insert(MtImMessage message);

    MtImMessage selectById(@Param("id") Long id);

    MtImMessage selectByCallbackMsgId(@Param("callbackMsgId") String callbackMsgId);

    MtImMessage selectByPlatformMessage(@Param("appId") Long appId,
                                        @Param("platformMsgId") Long platformMsgId);

    int mergeCallbackIntoInternalMessage(MtImMessage message);

    int updateDeliveryStatus(@Param("id") Long id,
                             @Param("deliveryStatus") String deliveryStatus,
                             @Param("errorCode") String errorCode,
                             @Param("errorSummary") String errorSummary,
                             @Param("updateTime") LocalDateTime updateTime);

    List<MtImMessage> selectRecentBySession(@Param("sessionId") Long sessionId,
                                            @Param("limit") int limit);

    List<MtImMessage> selectAfterMessageInConversation(@Param("conversationId") Long conversationId,
                                                        @Param("messageId") Long messageId);

    /**
     * 会话内最近一条商家人工发言，用于商家静默窗口判定。卡片不计入。
     */
    MtImMessage selectLatestMerchantMessageInConversation(@Param("conversationId") Long conversationId);

    List<MtImMessage> selectConversationHistoryBefore(@Param("conversationId") Long conversationId,
                                                       @Param("cursorTime") LocalDateTime cursorTime,
                                                       @Param("cursorId") Long cursorId,
                                                       @Param("limit") int limit);
}
