package com.gogoga.backend.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommentReplySentimentEnumTest {

    @Test
    void fromFoodCommentScoreMapsConfiguredRanges() {
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromFoodCommentScore(0));
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromFoodCommentScore(1));
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromFoodCommentScore(2));
        assertEquals(CommentReplySentimentEnum.NEUTRAL, CommentReplySentimentEnum.fromFoodCommentScore(3));
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromFoodCommentScore(4));
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromFoodCommentScore(5));
    }

    @Test
    void fromFoodCommentScoreReturnsUnknownForInvalidScores() {
        assertEquals(CommentReplySentimentEnum.UNKNOWN, CommentReplySentimentEnum.fromFoodCommentScore(null));
        assertEquals(CommentReplySentimentEnum.UNKNOWN, CommentReplySentimentEnum.fromFoodCommentScore(-1));
        assertEquals(CommentReplySentimentEnum.UNKNOWN, CommentReplySentimentEnum.fromFoodCommentScore(6));
    }
}
