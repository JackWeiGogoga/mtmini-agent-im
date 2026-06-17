package com.gogoga.backend.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 数据库异步任务基础运行指标。
 */
@Service
public class MtAsyncTaskMetricsService {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String taskType, String event) {
        if (taskType == null || event == null) {
            return;
        }
        counters.computeIfAbsent(taskType + "." + event, key -> new LongAdder()).increment();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.forEach((key, value) -> snapshot.put(key, value.longValue()));
        return snapshot;
    }
}
