package com.gogoga.backend.service.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmRateLimitKeyFactoryTest {

    private final LlmRateLimitKeyFactory keyFactory = new LlmRateLimitKeyFactory();

    @Test
    void shouldBuildStableSafeBucketWithoutSecrets() {
        String apiKey = "sk-production-secret";
        String model = "deepseek-v4-flash";

        LlmRateLimitKeyFactory.SafeBucket bucket = keyFactory.create(
                LlmRateLimitDescriptor.background("tokenhub", apiKey, model, "reply")
        );

        assertTrue(bucket.rateKey().startsWith("llm:rate:v1:TOKENHUB:"));
        assertTrue(bucket.cooldownKey().startsWith("llm:cooldown:v1:TOKENHUB:"));
        assertFalse(bucket.rateKey().contains(apiKey));
        assertFalse(bucket.rateKey().contains(model));
        assertFalse(bucket.safeBucketId().contains(apiKey));
    }

    @Test
    void shouldIsolateApiKeysAndModels() {
        LlmRateLimitKeyFactory.SafeBucket base = keyFactory.create(
                LlmRateLimitDescriptor.background("TOKENHUB", "key-a", "model-a", "reply")
        );
        LlmRateLimitKeyFactory.SafeBucket anotherKey = keyFactory.create(
                LlmRateLimitDescriptor.background("TOKENHUB", "key-b", "model-a", "reply")
        );
        LlmRateLimitKeyFactory.SafeBucket anotherModel = keyFactory.create(
                LlmRateLimitDescriptor.background("TOKENHUB", "key-a", "model-b", "reply")
        );

        assertNotEquals(base.rateKey(), anotherKey.rateKey());
        assertNotEquals(base.rateKey(), anotherModel.rateKey());
    }
}
