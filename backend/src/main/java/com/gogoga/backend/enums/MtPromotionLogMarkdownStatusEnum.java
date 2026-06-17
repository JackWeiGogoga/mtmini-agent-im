package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 推广日志 Markdown 状态枚举。
 */
@Getter
public enum MtPromotionLogMarkdownStatusEnum {

    PENDING("生成中"),
    READY("已生成"),
    FALLBACK("回退文案"),
    ;

    private final String desc;

    MtPromotionLogMarkdownStatusEnum(String desc) {
        this.desc = desc;
    }
}
