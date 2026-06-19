package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 数据库异步任务类型枚举。
 */
@Getter
public enum MtAsyncTaskTypeEnum {

    ORDER_NOTIFY("订单回调入库"),
    AUTH_SYNC_RIGHTS("授权后同步权益"),
    AUTH_BACKFILL_POI_ID("授权后补齐门店poiId"),
    AUTH_HISTORICAL_ORDER_BACKFILL("授权后历史订单补数"),
    AUTH_BLOCK_ORDER_PUSH("授权后停止订单推送"),
    ;

    private final String desc;

    MtAsyncTaskTypeEnum(String desc) {
        this.desc = desc;
    }
}
