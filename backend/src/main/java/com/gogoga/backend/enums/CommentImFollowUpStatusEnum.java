package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 差评匿名群聊跟进状态。
 */
@Getter
public enum CommentImFollowUpStatusEnum {

    PENDING("待处理"),
    GENERATED("消息已生成"),
    GROUP_CREATED("群聊已创建"),
    SUCCESS("消息已发送"),
    FAILED("处理失败"),
    SKIPPED("已跳过");

    private final String desc;

    CommentImFollowUpStatusEnum(String desc) {
        this.desc = desc;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED;
    }
}
