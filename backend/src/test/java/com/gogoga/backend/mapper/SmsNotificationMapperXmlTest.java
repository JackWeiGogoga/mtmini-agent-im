package com.gogoga.backend.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsNotificationMapperXmlTest {

    @Test
    void eventMapperShouldUseUniqueBusinessKeyAndAtomicClaimState() throws IOException {
        String xml = read("mapper/SmsNotificationEventMapper.xml");

        assertTrue(xml.contains("selectBySceneAndBizKey"));
        assertTrue(xml.contains("WHERE id = #{id} AND status = 'PENDING'"));
        assertTrue(xml.contains("WHERE status = 'PROCESSING' AND claimed_at IS NOT NULL"));
    }

    @Test
    void deliveryMapperShouldUseAtomicClaimAndIndependentProviderAuditFields() throws IOException {
        String xml = read("mapper/SmsNotificationDeliveryMapper.xml");

        assertTrue(xml.contains("WHERE id = #{id} AND status = 'PENDING'"));
        assertTrue(xml.contains("provider_request_id"));
        assertTrue(xml.contains("provider_serial_no"));
        assertTrue(xml.contains("recipient_phone"));
    }

    @Test
    void expiryQueryShouldIncludeImAutoReplyOnlyRightAndCursor() throws IOException {
        String xml = read("mapper/MtShopRightMapper.xml");

        assertTrue(xml.contains("selectActiveAppExpiryCandidatesByCursor"));
        assertTrue(xml.contains("shop_id &gt; #{lastShopId}"));
        assertTrue(xml.contains("'VIP_CODE', 'H5_PROMOTION', 'MT_APP_OPEN_ONLY', 'IM_AUTO_REPLY_ONLY'"));
        assertTrue(xml.contains("MAX(expire_at)"));
    }

    @Test
    void schemaShouldDeclareBothIdempotencyUniqueKeysAndPollingIndexes() throws IOException {
        String migration = read("db/migration/20260722_business_sms_notifications.sql");
        String baseline = read("db/full_schema.sql");

        assertTrue(migration.contains("UNIQUE KEY `uk_sms_notification_event_scene_biz` (`scene`, `biz_key`)"));
        assertTrue(migration.contains("UNIQUE KEY `uk_sms_notification_delivery_event_phone` (`event_id`, `recipient_phone`)"));
        assertTrue(migration.contains("`status`, `available_at`, `id`"));
        assertTrue(migration.contains("`status`, `claimed_at`, `id`"));
        assertTrue(baseline.contains("CREATE TABLE IF NOT EXISTS `sms_notification_event`"));
        assertTrue(baseline.contains("CREATE TABLE IF NOT EXISTS `sms_notification_delivery`"));
    }

    private String read(String resource) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
