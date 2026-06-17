package com.gogoga.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonUtilTest {

    @Test
    void shouldParsePureJsonObjectFromLlmText() {
        SamplePayload payload = JsonUtil.parseLlmJsonObject("{\"action\":\"KEEP\",\"value\":1}", SamplePayload.class);

        assertNotNull(payload);
        assertEquals("KEEP", payload.getAction());
        assertEquals(1, payload.getValue());
    }

    @Test
    void shouldParseFencedJsonObjectFromLlmText() {
        SamplePayload payload = JsonUtil.parseLlmJsonObject("""
                ```json
                {"action":"KEEP","value":2}
                ```
                """, SamplePayload.class);

        assertNotNull(payload);
        assertEquals("KEEP", payload.getAction());
        assertEquals(2, payload.getValue());
    }

    @Test
    void shouldParseJsonObjectWithExtraExplanation() {
        SamplePayload payload = JsonUtil.parseLlmJsonObject("""
                这是模型的结果：
                {"action":"DECREASE","value":3}
                请按以上 JSON 处理。
                """, SamplePayload.class);

        assertNotNull(payload);
        assertEquals("DECREASE", payload.getAction());
        assertEquals(3, payload.getValue());
    }

    @Test
    void shouldReturnNullWhenNoJsonObjectExists() {
        SamplePayload payload = JsonUtil.parseLlmJsonObject("no json here", SamplePayload.class);

        assertNull(payload);
    }

    public static class SamplePayload {

        private String action;

        private Integer value;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Integer getValue() {
            return value;
        }

        public void setValue(Integer value) {
            this.value = value;
        }
    }
}
