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

    public static CommentReplySentimentEnum fromScores(Integer productScore,
                                                       Integer packingScore,
                                                       Integer deliveryCommentScore,
                                                       Integer foodCommentScore) {
        return fromScore(resolveMinimumScore(
                productScore,
                packingScore,
                deliveryCommentScore,
                foodCommentScore
        ));
    }

    public static Integer resolveMinimumScore(Integer productScore,
                                              Integer packingScore,
                                              Integer deliveryCommentScore,
                                              Integer foodCommentScore) {
        Integer minimumScore = null;
        Integer[] scores = {productScore, packingScore, deliveryCommentScore, foodCommentScore};
        for (Integer score : scores) {
            if (!isValidScore(score)) {
                continue;
            }
            minimumScore = minimumScore == null ? score : Math.min(minimumScore, score);
        }
        return minimumScore;
    }

    private static CommentReplySentimentEnum fromScore(Integer score) {
        if (score == null) {
            return UNKNOWN;
        }
        if (score >= 1 && score <= 2) {
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
        return score != null && score >= 1 && score <= 5;
    }
}
