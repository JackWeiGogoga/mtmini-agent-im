package com.gogoga.backend.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtCommentImFollowUpMapperXmlTest {

    @Test
    void attemptClaimsAreAtomicAndTerminalUpdatesCannotOverwriteTerminalState() throws IOException {
        String xml = resource("mapper/MtCommentImFollowUpMapper.xml");

        assertTrue(xml.contains("create_group_attempted_at IS NULL"));
        assertTrue(xml.contains("send_attempted_at IS NULL"));
        assertTrue(xml.contains("status = 'GENERATED'"));
        assertTrue(xml.contains("status = 'GROUP_CREATED'"));
        assertTrue(xml.contains("status NOT IN ('SUCCESS', 'FAILED', 'SKIPPED')"));
        assertTrue(xml.contains("resetFailedForManualRetry"));
        assertTrue(xml.contains("create_group_attempted_at = CASE"));
        assertTrue(xml.contains("send_attempted_at = NULL"));
        assertTrue(xml.contains("AND status = 'FAILED'"));
    }

    @Test
    void processingLogAggregatesAllChainsInOneJoinWithoutNestedSelects() throws IOException {
        String xml = resource("mapper/CommentProcessingLogMapper.xml");

        assertTrue(xml.contains("FROM mt_comment c"));
        assertTrue(xml.contains("LEFT JOIN mt_comment_reply_generation"));
        assertTrue(xml.contains("LEFT JOIN mt_comment_complaint"));
        assertTrue(xml.contains("LEFT JOIN mt_comment_im_follow_up"));
        assertTrue(xml.contains("c.biz_code AS bizCode"));
        assertTrue(xml.contains("c.valid AS valid"));
        assertTrue(xml.contains("c.comment_type AS commentType"));
        assertTrue(xml.contains("NOT_APPLICABLE"));
        assertTrue(xml.contains("NOT_TRIGGERED"));
        assertTrue(xml.contains("c.delivery_comment_score BETWEEN 1 AND 5"));
        assertTrue(xml.contains("c.food_comment_score BETWEEN 1 AND 5"));
        assertTrue(xml.contains("WHEN 1 THEN 'BAD'"));
        org.junit.jupiter.api.Assertions.assertFalse(xml.contains("<collection"));
        org.junit.jupiter.api.Assertions.assertFalse(xml.contains("<association"));
    }

    @Test
    void commentStatsUseFourNonZeroScores() throws IOException {
        String xml = resource("mapper/MtCommentMapper.xml");

        assertTrue(xml.contains("product_score BETWEEN 1 AND 5"));
        assertTrue(xml.contains("packing_score BETWEEN 1 AND 5"));
        assertTrue(xml.contains("delivery_comment_score BETWEEN 1 AND 5"));
        assertTrue(xml.contains("food_comment_score BETWEEN 1 AND 5"));
        assertTrue(xml.contains("effective_score BETWEEN 1 AND 2"));
        assertEquals(3, xml.split("AND valid = 1", -1).length - 1);
        assertEquals(2, countOccurrences(xml, "AND comment_type IN (4, 5, 10)"));
        assertTrue(xml.contains("AND comment_type IN (4, 5)"));
    }

    private int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
