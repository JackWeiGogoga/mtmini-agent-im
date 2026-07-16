package com.gogoga.backend.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommentReplySentimentEnumTest {

    @Test
    void fromScoresUsesLowestScoreAcrossFourDimensions() {
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromScores(5, 4, 2, 3));
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromScores(5, 4, 3, 1));
        assertEquals(CommentReplySentimentEnum.NEUTRAL, CommentReplySentimentEnum.fromScores(5, 4, 3, 5));
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromScores(5, 4, 5, 4));
    }

    @Test
    void fromScoresIgnoresZeroBeforeTakingMinimum() {
        assertEquals(CommentReplySentimentEnum.NEUTRAL, CommentReplySentimentEnum.fromScores(0, 5, 0, 3));
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromScores(0, 5, 4, 0));
        assertEquals(4, CommentReplySentimentEnum.resolveMinimumScore(0, 5, 4, 0));
    }

    @Test
    void fromScoresReturnsUnknownWhenNoScoreParticipatesInRating() {
        assertEquals(CommentReplySentimentEnum.UNKNOWN, CommentReplySentimentEnum.fromScores(0, 0, 0, 0));
        assertEquals(CommentReplySentimentEnum.UNKNOWN, CommentReplySentimentEnum.fromScores(null, -1, 6, null));
        assertNull(CommentReplySentimentEnum.resolveMinimumScore(0, null, -1, 6));
    }

    @Test
    void fromScoresUsesAnySingleValidDimension() {
        assertEquals(CommentReplySentimentEnum.GOOD, CommentReplySentimentEnum.fromScores(5, null, 0, null));
        assertEquals(CommentReplySentimentEnum.BAD, CommentReplySentimentEnum.fromScores(null, 0, null, 2));
    }
}
