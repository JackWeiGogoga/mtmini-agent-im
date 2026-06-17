package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPoiTrafficDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 门店日流量数据 Mapper
 *
 * @author system
 * @since 2026-04-10
 */
@Mapper
public interface MtPoiTrafficDailyMapper {

    /**
     * 按门店、日期、流量类型查询
     *
     * @param shopId    门店 ID
     * @param bizDate   业务日期
     * @param flowProp  流量类型
     * @return 日流量记录
     */
    MtPoiTrafficDaily selectByShopIdAndBizDateAndFlowProp(@Param("shopId") Long shopId,
                                                          @Param("bizDate") LocalDate bizDate,
                                                          @Param("flowProp") Integer flowProp);

    /**
     * 幂等写入日流量数据
     *
     * @param record 日流量记录
     * @return 影响行数
     */
    int insertOrUpdate(MtPoiTrafficDaily record);

    /**
     * 汇总指定日期范围内的曝光量
     *
     * @param shopId           门店 ID
     * @param flowProp         流量类型
     * @param fromInclusive    开始日期
     * @param toExclusive      结束日期，不含
     * @return 曝光量合计
     */
    Integer sumExposureCountByShopIdAndBizDateBetween(@Param("shopId") Long shopId,
                                                      @Param("flowProp") Integer flowProp,
                                                      @Param("fromInclusive") LocalDate fromInclusive,
                                                      @Param("toExclusive") LocalDate toExclusive);
}
