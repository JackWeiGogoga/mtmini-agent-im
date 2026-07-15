package com.gogoga.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentImMessageComplianceServiceTest {

    private final CommentImMessageComplianceService service = new CommentImMessageComplianceService();

    @Test
    void allowsPoliteCollectionOfOrderAndMobileNumber() {
        assertTrue(service.validate("您好，很抱歉影响了体验🙏麻烦提供对应订单信息和方便联系的手机号码，我们尽快核实处理。").isPassed());
    }

    @Test
    void blocksCompensationReviewInducementAndOffPlatformContact() {
        assertBlocked("可以给您补偿优惠券", "COMPENSATION");
        assertBlocked("处理后麻烦删除评价", "REVIEW_INDUCEMENT");
        assertBlocked("请添加微信进一步沟通", "OFF_PLATFORM");
    }

    @Test
    void blocksHighRiskPersonalDataAndAbusiveContent() {
        assertBlocked("请提供身份证和验证码", "HIGH_RISK_PERSONAL_DATA");
        assertBlocked("平台垃圾，顾客白痴", "ABUSE_OR_ILLEGAL");
        assertFalse(service.validate(" ").isPassed());
    }

    private void assertBlocked(String content, String category) {
        var result = service.validate(content);
        assertFalse(result.isPassed());
        assertEquals(category, result.getCategory());
    }
}
