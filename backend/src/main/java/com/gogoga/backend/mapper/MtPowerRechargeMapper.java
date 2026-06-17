package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.vo.AdminPowerRechargeOrderDetailVO;
import com.gogoga.backend.vo.AdminPowerRechargeOrderRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店算力充值 Mapper
 *
 * @author system
 * @since 2026-03-23
 */
@Mapper
public interface MtPowerRechargeMapper {

    /**
     * 按主键查询
     *
     * @param id 主键 ID
     * @return 充值实体
     */
    MtPowerRecharge selectById(@Param("id") Long id);

    /**
     * 按充值单号查询
     *
     * @param rechargeNo 充值单号
     * @return 充值实体
     */
    MtPowerRecharge selectByRechargeNo(@Param("rechargeNo") String rechargeNo);

    /**
     * 按支付侧订单号查询
     *
     * @param paymentOrderNo 支付侧订单号
     * @return 充值实体
     */
    MtPowerRecharge selectByPaymentOrderNo(@Param("paymentOrderNo") String paymentOrderNo);

    /**
     * 查询店铺最新充值单
     *
     * @param shopId 店铺 ID
     * @return 最新充值实体
     */
    MtPowerRecharge selectLatestByShopId(@Param("shopId") Long shopId);

    MtPowerRecharge selectFirstSalesRechargeByShopId(@Param("shopId") Long shopId);

    List<MtPowerRecharge> selectByShopId(@Param("shopId") Long shopId);

    List<MtPowerRecharge> selectByShopIdAndCreateTimeBetween(
            @Param("shopId") Long shopId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<AdminPowerRechargeOrderRowVO> selectAdminRechargeOrderList(
            @Param("keyword") String keyword,
            @Param("payStatus") Integer payStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    AdminPowerRechargeOrderDetailVO selectAdminRechargeOrderDetailById(@Param("id") Long id);

    /**
     * 新增充值单
     *
     * @param recharge 充值实体
     * @return 影响行数
     */
    int insert(MtPowerRecharge recharge);

    /**
     * 按主键更新
     *
     * @param recharge 充值实体
     * @return 影响行数
     */
    int updateById(MtPowerRecharge recharge);

    /**
     * 标记充值成功
     *
     * @param id 主键 ID
     * @param transactionId 支付交易号
     * @param notifyPayload 通知内容
     * @param paidAt 支付时间
     * @param updateTime 更新时间
     * @return 影响行数
     */
    int markSuccess(
            @Param("id") Long id,
            @Param("transactionId") String transactionId,
            @Param("notifyPayload") String notifyPayload,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("updateTime") LocalDateTime updateTime
    );

    /**
     * 更新支付状态
     *
     * @param id 主键 ID
     * @param payStatus 支付状态
     * @param transactionId 支付交易号
     * @param notifyPayload 通知内容
     * @param updateTime 更新时间
     * @return 影响行数
     */
    int updateStatus(
            @Param("id") Long id,
            @Param("payStatus") Integer payStatus,
            @Param("transactionId") String transactionId,
            @Param("notifyPayload") String notifyPayload,
            @Param("updateTime") LocalDateTime updateTime
    );
}
