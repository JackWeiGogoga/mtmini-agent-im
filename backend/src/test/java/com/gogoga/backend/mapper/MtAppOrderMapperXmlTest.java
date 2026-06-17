package com.gogoga.backend.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MtAppOrderMapperXmlTest {

    @Test
    void settlementCandidateQueryShouldOnlyIncludePositivePaidOrders() throws IOException {
        String xml = new String(
                getClass().getClassLoader().getResourceAsStream("mapper/MtAppOrderMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(xml.contains("selectSettlementCandidates"));
        assertTrue(xml.contains("mao.pay_status = #{paidPayStatus}"));
        assertTrue(xml.contains("mao.real_pay_price > 0"));
    }
}
