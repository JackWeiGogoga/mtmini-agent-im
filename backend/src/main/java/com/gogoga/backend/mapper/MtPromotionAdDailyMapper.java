package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionAdDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 推广广告日汇总数据 Mapper
 */
@Mapper
public interface MtPromotionAdDailyMapper {

    MtPromotionAdDaily selectByShopIdAndBizDateAndChannel(@Param("shopId") Long shopId,
                                                          @Param("bizDate") LocalDate bizDate,
                                                          @Param("channel") Integer channel);

    MtPromotionAdDaily sumByShopIdAndBizDateRangeAndChannel(@Param("shopId") Long shopId,
                                                            @Param("startBizDate") LocalDate startBizDate,
                                                            @Param("endBizDate") LocalDate endBizDate,
                                                            @Param("channel") Integer channel);

    int insertOrUpdate(MtPromotionAdDaily record);
}
