package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 评论回复类型。
 */
@Getter
public enum CommentReplySentimentEnum {

    GOOD("好评"),
    NEUTRAL("中评"),
    BAD("差评"),
    UNKNOWN("未知");

    private final String desc;

    CommentReplySentimentEnum(String desc) {
        this.desc = desc;
    }

    public static CommentReplySentimentEnum fromProductAndPackingScore(Integer productScore, Integer packingScore) {
        return fromScore(resolveProductPackingScore(productScore, packingScore));
    }

    public static Integer resolveProductPackingScore(Integer productScore, Integer packingScore) {
        Integer validProductScore = isValidScore(productScore) ? productScore : null;
        Integer validPackingScore = isValidScore(packingScore) ? packingScore : null;
        if (validProductScore == null) {
            return validPackingScore;
        }
        if (validPackingScore == null) {
            return validProductScore;
        }
        return Math.min(validProductScore, validPackingScore);
    }

    private static CommentReplySentimentEnum fromScore(Integer score) {
        if (score == null) {
            return UNKNOWN;
        }
        if (score >= 0 && score <= 2) {
            return BAD;
        }
        if (score == 3) {
            return NEUTRAL;
        }
        if (score >= 4 && score <= 5) {
            return GOOD;
        }
        return UNKNOWN;
    }

    private static boolean isValidScore(Integer score) {
        return score != null && score >= 0 && score <= 5;
    }
}
