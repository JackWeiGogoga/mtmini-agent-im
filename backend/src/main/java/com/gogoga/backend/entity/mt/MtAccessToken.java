package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 美团授权 token 实体
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class MtAccessToken {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 访问 token
     */
    private String accessToken;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 商家唯一标识
     */
    private String opBizCode;

    /**
     * 刷新 token
     */
    private String refreshToken;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 授权状态：1有效 2需重新授权
     */
    private Integer authStatus;

    /**
     * 授权状态最近变更时间
     */
    private LocalDateTime authStatusChangedAt;

    /**
     * 最近一次鉴权失败时间
     */
    private LocalDateTime lastAuthFailureAt;

    /**
     * 订单推送是否已停止
     */
    private Boolean orderPushBlocked;

    /**
     * 订单推送最近一次同步时间
     */
    private LocalDateTime orderPushLastSyncAt;

    /**
     * 订单推送最近一次同步错误
     */
    private String orderPushLastSyncError;

    /**
     * 当前保存操作是否新建授权记录，不落库
     */
    private transient Boolean newlyCreated;
}
