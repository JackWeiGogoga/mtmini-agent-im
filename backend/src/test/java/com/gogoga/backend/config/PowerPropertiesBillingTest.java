package com.gogoga.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerPropertiesBillingTest {

    @Test
    void shouldConvertConfiguredMeituanPriceToPowerAmount() {
        PowerProperties properties = new PowerProperties();

        assertEquals(1500, properties.getBilling().calculateMeituanPaidApiPowerAmount());
    }

    @Test
    void shouldConvertPromptAndCompletionTokensToPowerAmount() {
        PowerProperties properties = new PowerProperties();

        assertEquals(1000, properties.getBilling().calculateLlmPowerAmount(1000L, 0L));
        assertEquals(1500, properties.getBilling().calculateLlmPowerAmount(0L, 1000L));
        assertEquals(1350, properties.getBilling().calculateLlmPowerAmount(600L, 500L));
    }
}
