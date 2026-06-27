package com.gogoga.backend.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommentReplySentimentEnumTest {

    @Test
    void fromProductAndPackingScoreUsesLowerScore() {
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromProductAndPackingScore(5, 2));
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromProductAndPackingScore(1, 5));
        assertEquals(CommentReplySentimentEnum.NEUTRAL, CommentReplySentimentEnum.fromProductAndPackingScore(3, 5));
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromProductAndPackingScore(5, 4));
    }

    @Test
    void fromProductAndPackingScoreReturnsUnknownWhenBothInvalid() {
        assertEquals(CommentReplySentimentEnum.UNKNOWN, CommentReplySentimentEnum.fromProductAndPackingScore(null, null));
        assertEquals(CommentReplySentimentEnum.UNKNOWN, CommentReplySentimentEnum.fromProductAndPackingScore(-1, 6));
    }

    @Test
    void fromProductAndPackingScoreUsesSingleValidScoreWhenOtherMissing() {
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromProductAndPackingScore(5, null));
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromProductAndPackingScore(null, 2));
    }

    @Test
    void fromProductAndPackingScoreDoesNotDependOnDeprecatedFoodScore() {
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromProductAndPackingScore(5, 4));
    }
}
