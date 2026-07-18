package com.gogoga.backend.service.llm;

import com.gogoga.backend.config.LlmRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmRateLimitPropertiesTest {

    @Test
    void shouldResolveConfiguredProviderAndModelIgnoringCase() {
        LlmRateLimitProperties properties = new LlmRateLimitProperties();
        LlmRateLimitProperties.Rule rule = new LlmRateLimitProperties.Rule();
        rule.setProvider("TOKENHUB");
        rule.setModel("deepseek-v4-flash");
        rule.setEffectiveRpm(50);
        rule.setBurst(1);
        properties.setRules(List.of(rule));

        LlmRateLimitProperties.ResolvedRule resolved = properties.resolve("tokenhub", "DEEPSEEK-V4-FLASH");

        assertEquals(50, resolved.effectiveRpm());
        assertEquals(1, resolved.burst());
    }

    @Test
    void shouldRejectInvalidWaitConfiguration() {
        LlmRateLimitProperties properties = new LlmRateLimitProperties();
        properties.setShortWaitMillis(5001L);
        properties.setMaxWaitMillis(5000L);

        assertThrows(IllegalStateException.class, properties::validate);
    }
}
