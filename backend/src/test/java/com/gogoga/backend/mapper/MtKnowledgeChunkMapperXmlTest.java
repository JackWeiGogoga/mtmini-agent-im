package com.gogoga.backend.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtKnowledgeChunkMapperXmlTest {

    @Test
    void vectorCandidatesAreShopScopedAndExcludeDisabledOrDeletedKnowledge() throws IOException {
        String xml;
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/MtKnowledgeChunkMapper.xml")) {
            assertNotNull(stream);
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
        }

        assertTrue(xml.contains("WHERE shop_id=#{shopId} AND enabled=1 AND deleted=0"));
        assertTrue(xml.contains("embedding_blob IS NOT NULL"));
        assertTrue(xml.contains("LIMIT #{limit}"));
    }
}
