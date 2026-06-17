package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtShop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 美团店铺 Mapper
 *
 * @author system
 * @since 2026-03-22
 */
@Mapper
public interface MtShopMapper {

    /**
     * 根据主键查询店铺
     *
     * @param id 主键 ID
     * @return 店铺实体
     */
    MtShop selectById(@Param("id") Long id);

    /**
     * 根据 epoiId 查询店铺
     *
     * @param epoiId epoiId
     * @return 店铺实体
     */
    MtShop selectByEpoiId(@Param("epoiId") String epoiId);

    /**
     * 根据 poiId 查询店铺
     *
     * @param poiId poiId
     * @return 店铺实体
     */
    MtShop selectByPoiId(@Param("poiId") String poiId);

    /**
     * 新增店铺
     *
     * @param shop 店铺实体
     * @return 影响行数
     */
    int insert(MtShop shop);

    int updatePromotionAdBackfillState(@Param("id") Long id,
                                       @Param("status") String status,
                                       @Param("backfillAt") java.time.LocalDateTime backfillAt,
                                       @Param("updateTime") java.time.LocalDateTime updateTime);

    int markHistoricalOrderBackfillProcessing(@Param("id") Long id,
                                              @Param("expectedStatus") String expectedStatus,
                                              @Param("processingStatus") String processingStatus,
                                              @Param("backfillAt") java.time.LocalDateTime backfillAt,
                                              @Param("updateTime") java.time.LocalDateTime updateTime);

    int markHistoricalOrderBackfillProcessingIfRetryable(@Param("id") Long id,
                                                         @Param("initStatus") String initStatus,
                                                         @Param("failedStatus") String failedStatus,
                                                         @Param("processingStatus") String processingStatus,
                                                         @Param("backfillAt") java.time.LocalDateTime backfillAt,
                                                         @Param("updateTime") java.time.LocalDateTime updateTime);

    int updateHistoricalOrderBackfillState(@Param("id") Long id,
                                           @Param("status") String status,
                                           @Param("backfillAt") java.time.LocalDateTime backfillAt,
                                           @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 按主键更新店铺
     *
     * @param shop 店铺实体
     * @return 影响行数
     */
    int updateById(MtShop shop);
}
