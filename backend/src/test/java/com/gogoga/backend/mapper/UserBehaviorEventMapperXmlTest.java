package com.gogoga.backend.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserBehaviorEventMapperXmlTest {

    @Test
    void mapperShouldParseWithMyBatis() throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream stream = mapperStream()) {
            new XMLMapperBuilder(
                    stream,
                    configuration,
                    "mapper/UserBehaviorEventMapper.xml",
                    configuration.getSqlFragments()
            ).parse();
        }

        assertTrue(configuration.hasStatement(
                "com.gogoga.backend.mapper.UserBehaviorEventMapper.selectAnalyticsRows"
        ));
    }

    @Test
    void eventInsertShouldBeIdempotentAndKeepSessionFields() throws IOException {
        String xml = mapperXml();

        assertTrue(xml.contains("client_event_id, session_id"));
        assertTrue(xml.contains("ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)"));
    }

    @Test
    void analyticsShouldStartFromVisibleShopsAndAggregateSettingHistory() throws IOException {
        String xml = mapperXml();

        assertTrue(xml.contains("FROM mt_shop sh"));
        assertTrue(xml.contains("LEFT JOIN ("));
        assertTrue(xml.contains("FROM mt_im_auto_reply_setting_history h"));
        assertTrue(xml.contains("h.change_source = 'H5'"));
        assertTrue(xml.contains("h.change_source = 'ADMIN'"));
        assertTrue(xml.contains("POWER_BALANCE_NOT_ENOUGH"));
        assertFalse(xml.contains("negative"));
        assertFalse(xml.contains("comment"));
    }

    @Test
    void analyticsShouldNotAggregateRetiredEvents() throws IOException {
        String xml = mapperXml();

        assertFalse(xml.contains("H5_POWER_TAB_CLICK"));
        assertFalse(xml.contains("H5_SUPPORT_TAB_CLICK"));
        assertFalse(xml.contains("H5_POWER_PAY_CLICK"));
        assertFalse(xml.contains("ADMIN_PROMOTION_"));
    }

    private String mapperXml() throws IOException {
        try (InputStream stream = mapperStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream mapperStream() {
        InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/UserBehaviorEventMapper.xml");
        assertNotNull(stream);
        return stream;
    }
}
