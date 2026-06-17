package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 推广广告分时明细数据 Mapper
 */
@Mapper
public interface MtPromotionAdHourlyDetailMapper {

    int insertOrUpdate(MtPromotionAdHourlyDetail record);

    MtPromotionAdHourlyDetail selectByShopIdAndBizDateAndChannelAndTimeRange(@Param("shopId") Long shopId,
                                                                              @Param("bizDate") LocalDate bizDate,
                                                                              @Param("channel") Integer channel,
                                                                              @Param("timeRange") String timeRange);

    List<MtPromotionAdHourlyDetail> selectByShopIdAndBizDateAndChannel(@Param("shopId") Long shopId,
                                                                        @Param("bizDate") LocalDate bizDate,
                                                                        @Param("channel") Integer channel);

    int deleteByShopIdAndBizDateAndChannel(@Param("shopId") Long shopId,
                                           @Param("bizDate") LocalDate bizDate,
                                           @Param("channel") Integer channel);
}
