package com.gogoga.backend.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
        assertTrue(xml.contains("NOT_APPLICABLE"));
        assertTrue(xml.contains("NOT_TRIGGERED"));
        org.junit.jupiter.api.Assertions.assertFalse(xml.contains("<collection"));
        org.junit.jupiter.api.Assertions.assertFalse(xml.contains("<association"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
