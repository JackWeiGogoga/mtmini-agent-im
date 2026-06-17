package com.gogoga.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PowerPropertiesTest {

    @Test
    void shouldAcceptDefaultRechargeConfig() {
        PowerProperties properties = new PowerProperties();

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void shouldRejectLegacyRechargeUnitConfigThatBreaksPackagePricing() {
        PowerProperties properties = new PowerProperties();
        properties.setMinPurchaseAmount(100);
        properties.setMaxPurchaseAmount(100_000);
        properties.setUnitPower(100);

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void shouldRejectRechargeAndBillingRateMismatch() {
        PowerProperties properties = new PowerProperties();
        properties.getBilling().setPowerPerYuan(50_000);

        assertThrows(IllegalStateException.class, properties::validate);
    }
}
