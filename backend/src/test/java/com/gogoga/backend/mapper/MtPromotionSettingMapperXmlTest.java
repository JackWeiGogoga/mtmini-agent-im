package com.gogoga.backend.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MtPromotionSettingMapperXmlTest {

    @Test
    void rightExpiryShutdownCandidateQueryShouldFilterBySwitchTokenAndMissingActiveRight() throws IOException {
        String xml = new String(
                getClass().getClassLoader().getResourceAsStream("mapper/MtPromotionSettingMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(xml.contains("selectRightExpiryShutdownCandidatesByCursor"));
        assertTrue(xml.contains("s.promotion_enabled = 1"));
        assertTrue(xml.contains("t.access_token IS NOT NULL"));
        assertTrue(xml.contains("t.op_biz_code IS NOT NULL"));
        assertTrue(xml.contains("sh.poi_id IS NOT NULL"));
        assertTrue(xml.contains("AND s.shop_id &gt; #{lastShopId}"));
        assertTrue(xml.contains("AND NOT EXISTS"));
        assertTrue(xml.contains("r.service_code IN ('H5_PROMOTION', 'VIP_CODE')"));
        assertTrue(xml.contains("r.expire_at &gt; #{now}"));
    }
}
