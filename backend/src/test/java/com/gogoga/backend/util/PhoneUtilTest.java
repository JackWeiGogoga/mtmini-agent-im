package com.gogoga.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhoneUtilTest {

    @Test
    void shouldMaskPhoneForSmsLogs() {
        assertEquals("138****8000", PhoneUtil.maskPhone("+86 138-0013-8000"));
        assertEquals("***", PhoneUtil.maskPhone("invalid"));
    }
}
