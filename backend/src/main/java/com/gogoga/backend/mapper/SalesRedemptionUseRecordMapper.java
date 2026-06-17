package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.SalesRedemptionUseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SalesRedemptionUseRecordMapper {

    int insert(SalesRedemptionUseRecord record);

    SalesRedemptionUseRecord selectByRechargeNo(@Param("rechargeNo") String rechargeNo);

    LocalDateTime selectFirstUseTimeByShopId(@Param("shopId") Long shopId);
}
