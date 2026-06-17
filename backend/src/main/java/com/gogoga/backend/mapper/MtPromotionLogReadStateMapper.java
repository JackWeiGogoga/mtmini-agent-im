package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionLogReadState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 推广日志已读状态 Mapper
 *
 * @author system
 * @since 2026-05-11
 */
@Mapper
public interface MtPromotionLogReadStateMapper {

    MtPromotionLogReadState selectByShopId(@Param("shopId") Long shopId);

    int upsertReadState(
            @Param("shopId") Long shopId,
            @Param("lastReadRecordId") Long lastReadRecordId,
            @Param("lastReadAt") LocalDateTime lastReadAt
    );
}
