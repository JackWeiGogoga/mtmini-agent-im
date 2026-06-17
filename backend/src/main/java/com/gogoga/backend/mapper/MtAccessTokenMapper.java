package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtAccessToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 美团授权 token Mapper
 *
 * @author system
 * @since 2026-03-22
 */
@Mapper
public interface MtAccessTokenMapper {

    /**
     * 根据主键查询
     *
     * @param id 主键 ID
     * @return token 实体
     */
    MtAccessToken selectById(@Param("id") Long id);

    /**
     * 根据商家唯一标识查询
     *
     * @param opBizCode 商家唯一标识
     * @return token 实体
     */
    MtAccessToken selectByOpBizCode(@Param("opBizCode") String opBizCode);

    /**
     * 根据 shopId 查询 token
     *
     * @param shopId 店铺 ID
     * @return token 实体
     */
    MtAccessToken selectByShopId(@Param("shopId") Long shopId);

    /**
     * 查询待刷新的 token
     *
     * @param refreshBefore 刷新截止时间
     * @return token 列表
     */
    List<MtAccessToken> selectRefreshCandidates(@Param("refreshBefore") LocalDateTime refreshBefore);

    /**
     * 查询可用于流量/推广数据同步的有效权益门店 token。
     *
     * @param now 当前时间
     * @return token 列表
     */
    List<MtAccessToken> selectTrafficSyncCandidates(@Param("now") LocalDateTime now);

    /**
     * 新增 token
     *
     * @param token token 实体
     * @return 影响行数
     */
    int insert(MtAccessToken token);

    /**
     * 按主键更新 token
     *
     * @param token token 实体
     * @return 影响行数
     */
    int updateById(MtAccessToken token);

    /**
     * 更新订单推送同步状态。
     *
     * @param id token ID
     * @param orderPushBlocked 订单推送是否已停止
     * @param orderPushLastSyncAt 最近同步时间
     * @param orderPushLastSyncError 最近同步错误
     * @return 影响行数
     */
    int updateOrderPushSyncState(
            @Param("id") Long id,
            @Param("orderPushBlocked") Boolean orderPushBlocked,
            @Param("orderPushLastSyncAt") LocalDateTime orderPushLastSyncAt,
            @Param("orderPushLastSyncError") String orderPushLastSyncError
    );

    /**
     * 按门店更新授权状态。
     *
     * @param shopId 店铺 ID
     * @param authStatus 授权状态
     * @param authStatusChangedAt 状态变更时间
     * @param lastAuthFailureAt 最近鉴权失败时间
     * @param updateTime 更新时间
     * @return 影响行数
     */
    int updateAuthStatusByShopId(
            @Param("shopId") Long shopId,
            @Param("authStatus") Integer authStatus,
            @Param("authStatusChangedAt") LocalDateTime authStatusChangedAt,
            @Param("lastAuthFailureAt") LocalDateTime lastAuthFailureAt,
            @Param("updateTime") LocalDateTime updateTime
    );
}
