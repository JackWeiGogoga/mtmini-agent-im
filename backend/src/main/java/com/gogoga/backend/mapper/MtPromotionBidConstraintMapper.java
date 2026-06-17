package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionBidConstraint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 门店推广出价约束 Mapper
 */
@Mapper
public interface MtPromotionBidConstraintMapper {

    MtPromotionBidConstraint selectActiveByShopId(
            @Param("shopId") Long shopId,
            @Param("now") LocalDateTime now
    );

    int upsert(MtPromotionBidConstraint constraint);
}
