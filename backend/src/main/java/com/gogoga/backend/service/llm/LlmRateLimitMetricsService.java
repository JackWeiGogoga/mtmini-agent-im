package com.gogoga.backend.service.llm;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 不含秘密的模型限流运行指标。
 */
@Service
public class LlmRateLimitMetricsService {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String safeBucketId, String event) {
        add(safeBucketId, event, 1L);
    }

    public void addWaitMillis(String safeBucketId, long waitMillis) {
        add(safeBucketId, "wait_millis", Math.max(waitMillis, 0L));
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        counters.forEach((key, value) -> result.put(key, value.longValue()));
        return result;
    }

    private void add(String safeBucketId, String event, long delta) {
        if (safeBucketId == null || event == null || delta <= 0L) {
            return;
        }
        counters.computeIfAbsent(safeBucketId + "." + event, key -> new LongAdder()).add(delta);
    }
}
