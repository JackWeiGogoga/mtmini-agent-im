package com.gogoga.backend.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullSchemaBaselineTest {

    @Test
    void freshDatabaseBaselineContainsOnlyImAndGenericKnowledgeBusinessTables() throws IOException {
        String schema = resource("db/full_schema.sql").toLowerCase();
        String init = resource("db/auth_init_data.sql").toLowerCase();

        assertTrue(schema.contains("create table `mt_im_auto_reply_setting`"));
        assertTrue(schema.contains("create table `mt_im_auto_reply_setting_history`"));
        assertTrue(schema.contains("create table `mt_im_message`"));
        assertTrue(schema.contains("create table `mt_knowledge_item`"));
        assertTrue(schema.contains("create table `mt_knowledge_document`"));
        assertTrue(schema.contains("create table `mt_knowledge_chunk`"));
        assertTrue(schema.contains("unique key `uk_mt_im_message_callback`"));
        assertTrue(schema.contains("unique key `uk_mt_im_message_platform`"));

        assertFalse(schema.contains("mt_comment"));
        assertFalse(schema.contains("negative_comment"));
        assertFalse(init.contains("comment:"));
        assertFalse(init.contains("评价"));
        assertFalse(init.contains("差评"));
        assertTrue(init.contains("knowledge:list"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
