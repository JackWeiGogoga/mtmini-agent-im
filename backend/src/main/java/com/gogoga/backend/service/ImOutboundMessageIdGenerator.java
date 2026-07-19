package com.gogoga.backend.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 生成可持久化、重试时复用的正数出站消息 ID。
 */
@Component
public class ImOutboundMessageIdGenerator {

    private static final long EPOCH = 1_767_225_600_000L;
    private static final long NODE_MASK = 0x3ffL;
    private static final long SEQUENCE_MASK = 0xfffL;

    private final long nodeId = resolveNodeId();
    private long lastMillis = -1L;
    private long sequence;

    public synchronized long nextId() {
        long currentMillis = System.currentTimeMillis();
        if (currentMillis < lastMillis) {
            currentMillis = lastMillis;
        }
        if (currentMillis == lastMillis) {
            sequence = (sequence + 1L) & SEQUENCE_MASK;
            if (sequence == 0L) {
                currentMillis = waitNextMillis(lastMillis);
            }
        } else {
            sequence = 0L;
        }
        lastMillis = currentMillis;
        return ((currentMillis - EPOCH) << 22) | (nodeId << 12) | sequence;
    }

    private long waitNextMillis(long previousMillis) {
        long currentMillis = System.currentTimeMillis();
        while (currentMillis <= previousMillis) {
            Thread.onSpinWait();
            currentMillis = System.currentTimeMillis();
        }
        return currentMillis;
    }

    private long resolveNodeId() {
        try {
            return Integer.toUnsignedLong(InetAddress.getLocalHost().getHostName().hashCode()) & NODE_MASK;
        } catch (Exception ignored) {
            return Integer.toUnsignedLong(System.identityHashCode(this)) & NODE_MASK;
        }
    }
}
