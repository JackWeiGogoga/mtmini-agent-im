package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPowerGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MtPowerGrantMapper {

    int insert(MtPowerGrant grant);

    MtPowerGrant selectByGrantNo(@Param("grantNo") String grantNo);

    List<MtPowerGrant> selectByShopId(@Param("shopId") Long shopId);

    List<MtPowerGrant> selectByShopIdAndCreateTimeBetween(
            @Param("shopId") Long shopId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
