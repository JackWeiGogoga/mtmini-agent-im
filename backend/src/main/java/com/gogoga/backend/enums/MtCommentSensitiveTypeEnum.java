package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 美团评论敏感类型及其业务策略。
 */
@Getter
public enum MtCommentSensitiveTypeEnum {

    VISIBLE_AND_RATED(4, true, true),
    HIDDEN_AND_RATED(5, false, true),
    VISIBLE_AND_UNRATED(10, true, false);

    private final int code;
    private final boolean contentVisible;
    private final boolean rated;

    MtCommentSensitiveTypeEnum(int code, boolean contentVisible, boolean rated) {
        this.code = code;
        this.contentVisible = contentVisible;
        this.rated = rated;
    }

    public static MtCommentSensitiveTypeEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MtCommentSensitiveTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    public static boolean isProcessable(Integer code) {
        return fromCode(code) != null;
    }

    public static boolean isContentVisible(Integer code) {
        MtCommentSensitiveTypeEnum type = fromCode(code);
        return type != null && type.contentVisible;
    }

    public static boolean isRated(Integer code) {
        MtCommentSensitiveTypeEnum type = fromCode(code);
        return type != null && type.rated;
    }
}
