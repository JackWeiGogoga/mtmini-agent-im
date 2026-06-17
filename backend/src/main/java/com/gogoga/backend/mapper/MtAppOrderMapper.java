package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.vo.AdminMtAppOrderRowVO;
import com.gogoga.backend.vo.AdminMtAppOrderSettlementRowVO;
import com.gogoga.backend.vo.AdminMtAppOrderVersionOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 美团订阅订单 Mapper
 *
 * @author system
 * @since 2026-03-22
 */
@Mapper
public interface MtAppOrderMapper {

    /**
     * 根据主键查询
     *
     * @param id 主键 ID
     * @return 订阅订单实体
     */
    MtAppOrder selectById(@Param("id") Long id);

    /**
     * 根据订单号查询
     *
     * @param orderId 订单号
     * @return 订阅订单实体
     */
    MtAppOrder selectByOrderId(@Param("orderId") String orderId);

    /**
     * 查询最新订单时间
     *
     * @return 最新订单时间
     */
    Long selectLatestOrderTime();

    /**
     * 查询待补偿权益的订单
     *
     * @return 订单列表
     */
    List<MtAppOrder> selectPendingRightOrders();

    /**
     * 后台分页查询订阅订单
     */
    List<AdminMtAppOrderRowVO> selectAdminPage(
            @Param("orderId") String orderId,
            @Param("orderViewId") String orderViewId,
            @Param("poiId") String poiId,
            @Param("shopName") String shopName,
            @Param("payStatus") Integer payStatus,
            @Param("rightStatus") String rightStatus,
            @Param("version") String version,
            @Param("versionName") String versionName,
            @Param("targetUserId") String targetUserId,
            @Param("orderStartTime") Long orderStartTime,
            @Param("orderEndTime") Long orderEndTime,
            @Param("scopeType") String scopeType,
            @Param("currentUserId") String currentUserId,
            @Param("visibleUserIds") List<String> visibleUserIds
    );

    /**
     * 后台查询当前可见范围内的订阅订单版本筛选项。
     */
    List<AdminMtAppOrderVersionOptionVO> selectAdminVersionOptions(
            @Param("scopeType") String scopeType,
            @Param("currentUserId") String currentUserId,
            @Param("visibleUserIds") List<String> visibleUserIds
    );

    /**
     * 后台查询结算统计候选订阅订单。
     */
    List<AdminMtAppOrderSettlementRowVO> selectSettlementCandidates(
            @Param("poiId") String poiId,
            @Param("paidPayStatus") Integer paidPayStatus,
            @Param("serviceStartTime") Long serviceStartTime,
            @Param("serviceEndTime") Long serviceEndTime,
            @Param("scopeType") String scopeType,
            @Param("currentUserId") String currentUserId,
            @Param("visibleUserIds") List<String> visibleUserIds
    );

    /**
     * 新增订阅订单
     *
     * @param order 订阅订单实体
     * @return 影响行数
     */
    int insert(MtAppOrder order);

    /**
     * 按主键更新订阅订单
     *
     * @param order 订阅订单实体
     * @return 影响行数
     */
    int updateById(MtAppOrder order);
}
