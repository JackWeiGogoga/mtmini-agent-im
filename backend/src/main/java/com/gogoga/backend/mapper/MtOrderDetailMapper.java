package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtOrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 美团订单明细 Mapper
 *
 * @author system
 * @since 2026-03-22
 */
@Mapper
public interface MtOrderDetailMapper {

    /**
     * 新增订单明细
     *
     * @param detail 订单明细
     * @return 影响行数
     */
    int insert(MtOrderDetail detail);

    /**
     * 按订单号删除订单明细
     *
     * @param mtOrderId 订单号
     * @return 影响行数
     */
    int deleteByMtOrderId(@Param("mtOrderId") String mtOrderId);
}
