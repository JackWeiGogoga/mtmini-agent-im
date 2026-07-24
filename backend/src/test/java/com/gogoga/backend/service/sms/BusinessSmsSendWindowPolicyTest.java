package com.gogoga.backend.service.sms;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessSmsSendWindowPolicyTest {

    private final BusinessSmsSendWindowPolicy policy = new BusinessSmsSendWindowPolicy();

    @Test
    void shouldKeepTimesWithinHalfOpenSendWindow() {
        LocalDateTime atStart = LocalDateTime.of(2026, 7, 24, 8, 0);
        LocalDateTime beforeEnd = LocalDateTime.of(2026, 7, 24, 21, 59, 59);

        assertTrue(policy.isWithinWindow(atStart));
        assertTrue(policy.isWithinWindow(beforeEnd));
        assertEquals(atStart, policy.resolveAvailableAt(atStart));
        assertEquals(beforeEnd, policy.resolveAvailableAt(beforeEnd));
    }

    @Test
    void shouldMoveEarlyMorningAndNightToNextDayAtEight() {
        LocalDateTime earlyMorning = LocalDateTime.of(2026, 7, 24, 2, 0, 11);
        LocalDateTime atEnd = LocalDateTime.of(2026, 7, 24, 22, 0);
        LocalDateTime nextDayAtEight = LocalDateTime.of(2026, 7, 25, 8, 0);

        assertFalse(policy.isWithinWindow(earlyMorning));
        assertFalse(policy.isWithinWindow(atEnd));
        assertEquals(nextDayAtEight, policy.resolveAvailableAt(earlyMorning));
        assertEquals(nextDayAtEight, policy.resolveAvailableAt(atEnd));
    }
}
