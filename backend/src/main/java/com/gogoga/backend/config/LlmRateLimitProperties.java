package com.gogoga.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容模型集群限流配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm.rate-limit")
public class LlmRateLimitProperties {

    private boolean enabled = true;
    private boolean observeOnly = false;
    private int defaultEffectiveRpm = 30;
    private int defaultBurst = 1;
    private long shortWaitMillis = 5000L;
    private long maxWaitMillis = 120000L;
    private long interactiveMaxWaitMillis = 2000L;
    private long defaultCooldownSeconds = 60L;
    private long stateTtlSeconds = 180L;
    private long rescheduleJitterMillis = 3000L;
    private List<Rule> rules = new ArrayList<>();

    @PostConstruct
    public void validate() {
        requirePositive(defaultEffectiveRpm, "llm.rate-limit.default-effective-rpm");
        requirePositive(defaultBurst, "llm.rate-limit.default-burst");
        requirePositive(shortWaitMillis, "llm.rate-limit.short-wait-millis");
        requirePositive(maxWaitMillis, "llm.rate-limit.max-wait-millis");
        requirePositive(interactiveMaxWaitMillis, "llm.rate-limit.interactive-max-wait-millis");
        requirePositive(defaultCooldownSeconds, "llm.rate-limit.default-cooldown-seconds");
        requirePositive(stateTtlSeconds, "llm.rate-limit.state-ttl-seconds");
        if (shortWaitMillis > maxWaitMillis) {
            throw new IllegalStateException("llm.rate-limit.short-wait-millis 不能大于 max-wait-millis");
        }
        if (rescheduleJitterMillis < 0L) {
            throw new IllegalStateException("llm.rate-limit.reschedule-jitter-millis 不能小于 0");
        }
        for (Rule rule : rules) {
            if (rule == null || StringUtils.isBlank(rule.getProvider()) || StringUtils.isBlank(rule.getModel())) {
                throw new IllegalStateException("llm.rate-limit.rules 必须配置 provider 和 model");
            }
            requirePositive(rule.getEffectiveRpm(), "llm.rate-limit.rules.effective-rpm");
            requirePositive(rule.getBurst(), "llm.rate-limit.rules.burst");
        }
    }

    public ResolvedRule resolve(String provider, String model) {
        for (Rule rule : rules) {
            if (rule != null
                    && StringUtils.equalsIgnoreCase(StringUtils.trim(rule.getProvider()), StringUtils.trim(provider))
                    && StringUtils.equalsIgnoreCase(StringUtils.trim(rule.getModel()), StringUtils.trim(model))) {
                return new ResolvedRule(rule.getEffectiveRpm(), rule.getBurst());
            }
        }
        return new ResolvedRule(defaultEffectiveRpm, defaultBurst);
    }

    private void requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalStateException(name + " 必须大于 0");
        }
    }

    @Data
    public static class Rule {
        private String provider;
        private String model;
        private int effectiveRpm = 30;
        private int burst = 1;
    }

    public record ResolvedRule(int effectiveRpm, int burst) {
    }
}
