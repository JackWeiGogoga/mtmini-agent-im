package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionMetricSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface MtPromotionMetricSnapshotMapper {

    MtPromotionMetricSnapshot selectByShopIdAndBizDateAndSlotTime(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate,
            @Param("slotTime") LocalDateTime slotTime
    );

    MtPromotionMetricSnapshot selectLatestByShopIdAndSlotTimeRange(
            @Param("shopId") Long shopId,
            @Param("startSlotTime") LocalDateTime startSlotTime,
            @Param("endSlotTime") LocalDateTime endSlotTime
    );

    int insertOrUpdate(MtPromotionMetricSnapshot snapshot);
}
