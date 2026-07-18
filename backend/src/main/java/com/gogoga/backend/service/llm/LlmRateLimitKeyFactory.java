package com.gogoga.backend.service.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 构造不含秘密的稳定限流 key。
 */
@Component
public class LlmRateLimitKeyFactory {

    public SafeBucket create(LlmRateLimitDescriptor descriptor) {
        if (descriptor == null || StringUtils.isBlank(descriptor.apiKey()) || StringUtils.isBlank(descriptor.model())) {
            throw new IllegalArgumentException("模型限流维度不完整");
        }
        String provider = StringUtils.defaultIfBlank(descriptor.provider(), "UNKNOWN")
                .trim().toUpperCase(Locale.ROOT);
        String apiKeyFingerprint = sha256(descriptor.apiKey()).substring(0, 32);
        String modelFingerprint = sha256(descriptor.model()).substring(0, 16);
        String bucketId = provider + ":" + apiKeyFingerprint + ":" + modelFingerprint;
        return new SafeBucket(
                "llm:rate:v1:" + bucketId,
                "llm:cooldown:v1:" + bucketId,
                provider,
                descriptor.model(),
                apiKeyFingerprint.substring(0, 8) + modelFingerprint.substring(0, 4)
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public record SafeBucket(
            String rateKey,
            String cooldownKey,
            String provider,
            String model,
            String safeBucketId
    ) {
    }
}
