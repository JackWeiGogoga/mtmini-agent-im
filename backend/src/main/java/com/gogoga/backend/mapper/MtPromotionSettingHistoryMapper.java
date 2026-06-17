package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionSettingHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店推广配置历史 Mapper
 */
@Mapper
public interface MtPromotionSettingHistoryMapper {

    int insert(MtPromotionSettingHistory history);

    Integer selectMaxVersionNoByShopId(@Param("shopId") Long shopId);

    List<MtPromotionSettingHistory> selectLatestByShopId(
            @Param("shopId") Long shopId,
            @Param("limit") int limit
    );

    MtPromotionSettingHistory selectLatestByShopIdBeforeCreateTime(
            @Param("shopId") Long shopId,
            @Param("createTime") LocalDateTime createTime
    );

    List<MtPromotionSettingHistory> selectByShopIdAndCreateTimeRange(
            @Param("shopId") Long shopId,
            @Param("startCreateTime") LocalDateTime startCreateTime,
            @Param("endCreateTime") LocalDateTime endCreateTime
    );
}
