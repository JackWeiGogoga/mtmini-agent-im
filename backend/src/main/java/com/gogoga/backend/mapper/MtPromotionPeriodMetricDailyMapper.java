package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionPeriodMetricDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 门店推广期日统计预聚合 Mapper。
 */
@Mapper
public interface MtPromotionPeriodMetricDailyMapper {

    MtPromotionPeriodMetricDaily selectByShopIdAndBizDate(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate
    );

    List<MtPromotionPeriodMetricDaily> selectByShopIdsAndBizDateRange(
            @Param("shopIds") List<Long> shopIds,
            @Param("startBizDate") LocalDate startBizDate,
            @Param("endBizDate") LocalDate endBizDate
    );

    int insertOrUpdate(MtPromotionPeriodMetricDaily metric);
}
