package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 推广广告实时快照 Mapper
 */
@Mapper
public interface MtPromotionAdRealtimeSnapshotMapper {

    MtPromotionAdRealtimeSnapshot selectByShopIdAndBizDateAndSlotTime(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate,
            @Param("slotTime") LocalDateTime slotTime
    );

    MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeRange(
            @Param("shopId") Long shopId,
            @Param("startSlotTime") LocalDateTime startSlotTime,
            @Param("endSlotTime") LocalDateTime endSlotTime
    );

    MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeBefore(
            @Param("shopId") Long shopId,
            @Param("slotTime") LocalDateTime slotTime
    );

    MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeBeforeOrEqual(
            @Param("shopId") Long shopId,
            @Param("slotTime") LocalDateTime slotTime
    );

    MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
            @Param("shopId") Long shopId,
            @Param("startSlotTime") LocalDateTime startSlotTime,
            @Param("endSlotTime") LocalDateTime endSlotTime
    );

    MtPromotionAdRealtimeSnapshot selectEarliestByShopIdAndSlotTimeRange(
            @Param("shopId") Long shopId,
            @Param("startSlotTime") LocalDateTime startSlotTime,
            @Param("endSlotTime") LocalDateTime endSlotTime
    );

    int insertOrUpdate(MtPromotionAdRealtimeSnapshot snapshot);
}
