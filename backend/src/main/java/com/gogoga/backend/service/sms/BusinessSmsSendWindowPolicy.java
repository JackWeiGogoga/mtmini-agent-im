package com.gogoga.backend.service.sms;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 业务营销短信统一发送窗口。
 */
@Component
public class BusinessSmsSendWindowPolicy {

    private static final LocalTime WINDOW_START = LocalTime.of(8, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(22, 0);

    /**
     * 窗口内保持原时间，窗口外统一延迟到下一自然日 08:00。
     */
    public LocalDateTime resolveAvailableAt(LocalDateTime requestedAt) {
        Objects.requireNonNull(requestedAt, "业务短信计划时间不能为空");
        if (isWithinWindow(requestedAt)) {
            return requestedAt;
        }
        return requestedAt.toLocalDate().plusDays(1).atTime(WINDOW_START);
    }

    public boolean isWithinWindow(LocalDateTime time) {
        Objects.requireNonNull(time, "业务短信时间不能为空");
        LocalTime localTime = time.toLocalTime();
        return !localTime.isBefore(WINDOW_START) && localTime.isBefore(WINDOW_END);
    }
}
