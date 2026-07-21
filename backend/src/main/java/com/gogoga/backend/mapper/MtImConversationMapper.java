package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MtImConversationMapper {

    int insert(MtImConversation conversation);

    MtImConversation selectById(@Param("id") Long id);

    MtImConversation selectByShopAndOpenUser(@Param("shopId") Long shopId,
                                              @Param("openUserId") Long openUserId);

    MtImConversation selectByShopAndOpenUserForUpdate(@Param("shopId") Long shopId,
                                                       @Param("openUserId") Long openUserId);

    int updateCurrentSession(MtImConversation conversation);

    int incrementMessageAggregate(@Param("id") Long id,
                                  @Param("messageId") Long messageId,
                                  @Param("senderRole") String senderRole,
                                  @Param("eventTime") java.time.LocalDateTime eventTime);
}
