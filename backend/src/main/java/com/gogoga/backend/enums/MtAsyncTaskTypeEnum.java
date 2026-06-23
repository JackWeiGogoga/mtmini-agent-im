package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 数据库异步任务类型枚举。
 */
@Getter
public enum MtAsyncTaskTypeEnum {

    AUTH_SYNC_RIGHTS("授权后同步权益"),
    AUTH_BLOCK_ORDER_PUSH("授权后停止订单推送"),
    AUTH_COMMENT_BACKFILL("授权后回补近7天评价"),
    COMMENT_AUTO_REPLY("评价自动回复"),
    KNOWLEDGE_DOCUMENT_PARSE("知识文档解析"),
    ;

    private final String desc;

    MtAsyncTaskTypeEnum(String desc) {
        this.desc = desc;
    }
}
