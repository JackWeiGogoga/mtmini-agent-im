package com.gogoga.backend.mapper;

import com.gogoga.backend.dto.ImBroadcastTargetDTO;
import com.gogoga.backend.entity.mt.MtImConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /**
     * 按 (last_user_message_at, id) 倒序游标扫描群发目标人群，命中覆盖索引，不回表。
     *
     * <p>用 last_user_message_at 而非 last_message_at：群发与 AI 回复都会推高后者，
     * 以它为准会让被群发过的顾客永远停留在活跃窗口内。倒序则保证超出上限时保留最近活跃的顾客。</p>
     */
    List<ImBroadcastTargetDTO> selectBroadcastTargets(@Param("shopId") Long shopId,
                                                      @Param("activeSince") java.time.LocalDateTime activeSince,
                                                      @Param("cursorLastUserMessageAt") java.time.LocalDateTime cursorLastUserMessageAt,
                                                      @Param("cursorId") Long cursorId,
                                                      @Param("limit") int limit);

    /**
     * 统计门店在活跃窗口内的目标人数，仅在批次触达人数上限时调用一次，用于回填截断人数。
     */
    long countBroadcastTargets(@Param("shopId") Long shopId,
                               @Param("activeSince") java.time.LocalDateTime activeSince);
}
