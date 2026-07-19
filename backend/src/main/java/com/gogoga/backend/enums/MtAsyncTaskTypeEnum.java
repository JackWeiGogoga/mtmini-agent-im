package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 数据库异步任务类型枚举。
 */
@Getter
public enum MtAsyncTaskTypeEnum {

    AUTH_SYNC_RIGHTS("授权后同步权益"),
    AUTH_BACKFILL_POI_ID("授权后补齐门店poiId"),
    AUTH_BLOCK_ORDER_PUSH("授权后停止订单推送"),
    AUTH_CONFIGURE_IM("授权后初始化门店IM"),
    KNOWLEDGE_DOCUMENT_PARSE("知识文档解析"),
    IM_AUTO_REPLY("IM多轮自动回复"),
    ;

    private final String desc;

    MtAsyncTaskTypeEnum(String desc) {
        this.desc = desc;
    }
}
