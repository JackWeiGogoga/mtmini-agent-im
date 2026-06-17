package com.gogoga.backend.param;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * H5 推广配置更新参数测试
 *
 * @author system
 * @since 2026-03-24
 */
class MtH5PromotionSettingsUpdateParamTest {

    @Test
    void shouldExposeStrategyConfigField() throws NoSuchFieldException {
        Field field = MtH5PromotionSettingsUpdateParam.class.getDeclaredField("strategyConfigJson");

        assertNotNull(field);
        assertEquals(String.class, field.getType());
    }
}
