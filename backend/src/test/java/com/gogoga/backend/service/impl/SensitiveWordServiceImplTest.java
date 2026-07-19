package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.SensitiveWordsProperties;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveWordServiceImplTest {

    @Test
    void detectsEnabledWordsAfterNormalizationAndIgnoresDisabledWords() {
        MtProperties properties = new MtProperties();
        SensitiveWordsProperties wordsProperties = new SensitiveWordsProperties();
        SensitiveWordsProperties.Category category = category("OFF_PLATFORM", "站外导流违规词", "HIGH");
        category.getWords().add(word("微信", null, true));
        category.getWords().add(word("支付宝", null, false));
        wordsProperties.getCategories().add(category);

        SensitiveWordServiceImpl service = new SensitiveWordServiceImpl(properties, wordsProperties);

        SensitiveWordHitDTO hit = service.detect("请加 微-信 处理");
        assertTrue(hit.isHit());
        assertEquals(1, hit.getHitCount());
        assertEquals("HIGH", hit.getHighestSeverity());
        assertEquals("站外导流违规词", hit.getCategories().get(0));
        assertFalse(service.detect("支持支付宝").isHit());
    }

    @Test
    void calculatesHighestSeverityAcrossCategoriesWithoutExposingWords() {
        MtProperties properties = new MtProperties();
        SensitiveWordsProperties wordsProperties = new SensitiveWordsProperties();
        SensitiveWordsProperties.Category medium = category("AD", "广告违规", "MEDIUM");
        medium.getWords().add(word("最好", null, true));
        SensitiveWordsProperties.Category high = category("ABUSE", "辱骂攻击", "MEDIUM");
        high.getWords().add(word("废物", "HIGH", true));
        wordsProperties.getCategories().add(medium);
        wordsProperties.getCategories().add(high);
        SensitiveWordServiceImpl service = new SensitiveWordServiceImpl(properties, wordsProperties);

        SensitiveWordHitDTO hit = service.detect("你是废物，这就是最 好的处理");

        assertTrue(hit.isHit());
        assertEquals(2, hit.getHitCount());
        assertEquals("HIGH", hit.getHighestSeverity());
        assertFalse(service.toSafeMessage(hit).contains("废物"));
        assertFalse(service.toSafeMessage(hit).contains("最好"));
    }

    @Test
    void canBeDisabledAndRejectsKnowledgeWithSafeCategoryOnly() {
        MtProperties properties = new MtProperties();
        SensitiveWordsProperties wordsProperties = new SensitiveWordsProperties();
        SensitiveWordsProperties.Category category = category("OFF_PLATFORM", "站外导流违规词", "HIGH");
        category.getWords().add(word("微信", null, true));
        wordsProperties.getCategories().add(category);
        SensitiveWordServiceImpl service = new SensitiveWordServiceImpl(properties, wordsProperties);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.validateKnowledgeText(10L, "QA", "请加微信")
        );
        assertTrue(exception.getMessage().contains("站外导流违规词"));
        assertFalse(exception.getMessage().contains("微信"));

        properties.getSensitiveWord().setEnabled(false);
        assertFalse(service.detect("请加微信").isHit());
    }

    private SensitiveWordsProperties.Category category(String code, String name, String severity) {
        SensitiveWordsProperties.Category category = new SensitiveWordsProperties.Category();
        category.setCode(code);
        category.setName(name);
        category.setSeverity(severity);
        return category;
    }

    private SensitiveWordsProperties.Word word(String text, String severity, boolean enabled) {
        SensitiveWordsProperties.Word word = new SensitiveWordsProperties.Word();
        word.setText(text);
        word.setSeverity(severity);
        word.setEnabled(enabled);
        return word;
    }
}
