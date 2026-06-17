package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionRecentOrderStatistic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 门店核心定价最近订单统计 Mapper。
 */
@Mapper
public interface MtPromotionRecentOrderStatisticMapper {

    MtPromotionRecentOrderStatistic selectByShopIdAndBizDate(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate
    );

    int upsert(MtPromotionRecentOrderStatistic statistic);

    int deleteByShopIdAndBizDate(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate
    );

    int deleteByShopIdAndBizDateOnOrAfter(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate
    );
}
