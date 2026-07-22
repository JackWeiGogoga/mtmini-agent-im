package com.gogoga.backend.service.sms.notification;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 业务短信进程内运行指标。
 */
@Service
public class SmsNotificationMetricsService {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String scene, String event) {
        if (scene == null || event == null) {
            return;
        }
        counters.computeIfAbsent(scene + "." + event, key -> new LongAdder()).increment();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.forEach((key, value) -> snapshot.put(key, value.longValue()));
        return snapshot;
    }
}
