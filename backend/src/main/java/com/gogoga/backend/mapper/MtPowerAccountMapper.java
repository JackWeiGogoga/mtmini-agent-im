package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPowerAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 门店算力账户 Mapper
 *
 * @author system
 * @since 2026-03-23
 */
@Mapper
public interface MtPowerAccountMapper {

    MtPowerAccount selectById(@Param("id") Long id);

    MtPowerAccount selectByShopId(@Param("shopId") Long shopId);

    int initAccountIfAbsent(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    int insert(MtPowerAccount account);

    int updateById(MtPowerAccount account);

    int addPaidRecharge(
            @Param("shopId") Long shopId,
            @Param("powerAmount") Integer powerAmount,
            @Param("version") Integer version,
            @Param("now") LocalDateTime now
    );

    int addAdminGrant(
            @Param("shopId") Long shopId,
            @Param("powerAmount") Integer powerAmount,
            @Param("version") Integer version,
            @Param("now") LocalDateTime now
    );

    int deductBySource(
            @Param("shopId") Long shopId,
            @Param("powerAmount") Integer powerAmount,
            @Param("grantAmount") Integer grantAmount,
            @Param("rechargeAmount") Integer rechargeAmount,
            @Param("version") Integer version,
            @Param("now") LocalDateTime now
    );
}
