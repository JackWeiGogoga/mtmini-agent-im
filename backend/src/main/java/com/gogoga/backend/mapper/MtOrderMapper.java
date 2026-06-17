package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtOrderHourlyAggregate;
import com.gogoga.backend.entity.mt.MtOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 美团订单 Mapper
 *
 * @author system
 * @since 2026-03-22
 */
@Mapper
public interface MtOrderMapper {

    /**
     * 根据主键查询
     *
     * @param id 主键 ID
     * @return 订单实体
     */
    MtOrder selectById(@Param("id") Long id);

    /**
     * 根据订单号查询
     *
     * @param orderId 订单号
     * @return 订单实体
     */
    MtOrder selectByOrderId(@Param("orderId") String orderId);

    /**
     * 根据订单号查询并加锁，串行化同一订单的明细重建。
     *
     * @param orderId 订单号
     * @return 订单实体
     */
    MtOrder selectByOrderIdForUpdate(@Param("orderId") String orderId);

    /**
     * 新增订单
     *
     * @param order 订单实体
     * @return 影响行数
     */
    int insert(MtOrder order);

    /**
     * 按主键更新订单
     *
     * @param order 订单实体
     * @return 影响行数
     */
    int updateById(MtOrder order);

    /**
     * 统计门店在指定下单时间范围内的订单数
     *
     * @param shopId 门店 ID
     * @param startEpochSeconds 起始秒级时间戳，含
     * @param endEpochSeconds 结束秒级时间戳，不含
     * @return 订单数
     */
    Integer countOrdersByShopIdAndOrderSendTimeBetween(
            @Param("shopId") Long shopId,
            @Param("startEpochSeconds") Long startEpochSeconds,
            @Param("endEpochSeconds") Long endEpochSeconds
    );

    /**
     * 汇总门店指定时间范围内未取消订单收入。
     *
     * @param shopId 店铺 ID
     * @param canceledStatus 已取消状态码
     * @param startEpochSeconds 起始时间
     * @param endEpochSeconds 结束时间
     * @return 收入
     */
    BigDecimal sumIncomeByShopIdAndOrderSendTimeBetweenExcludingStatus(
            @Param("shopId") Long shopId,
            @Param("canceledStatus") Integer canceledStatus,
            @Param("startEpochSeconds") Long startEpochSeconds,
            @Param("endEpochSeconds") Long endEpochSeconds
    );

    /**
     * 统计门店指定时间范围内未取消订单量
     *
     * @param shopId 店铺 ID
     * @param canceledStatus 已取消状态码
     * @param startEpochSeconds 起始时间
     * @param endEpochSeconds 结束时间
     * @return 订单量
     */
    Integer countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(
            @Param("shopId") Long shopId,
            @Param("canceledStatus") Integer canceledStatus,
            @Param("startEpochSeconds") Long startEpochSeconds,
            @Param("endEpochSeconds") Long endEpochSeconds
    );

    /**
     * 按日期和小时聚合门店历史订单
     *
     * @param shopId 店铺 ID
     * @param canceledStatus 已取消状态码
     * @param startEpochSeconds 起始时间
     * @param endEpochSeconds 结束时间
     * @return 聚合结果
     */
    List<MtOrderHourlyAggregate> selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(
            @Param("shopId") Long shopId,
            @Param("canceledStatus") Integer canceledStatus,
            @Param("startEpochSeconds") Long startEpochSeconds,
            @Param("endEpochSeconds") Long endEpochSeconds
    );
}
