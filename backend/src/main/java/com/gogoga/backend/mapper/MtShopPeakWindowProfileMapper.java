package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtShopPeakWindowProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 门店高峰画像 Mapper
 */
@Mapper
public interface MtShopPeakWindowProfileMapper {

    MtShopPeakWindowProfile selectByShopIdAndDayType(
            @Param("shopId") Long shopId,
            @Param("dayType") String dayType
    );

    int upsert(MtShopPeakWindowProfile profile);
}
