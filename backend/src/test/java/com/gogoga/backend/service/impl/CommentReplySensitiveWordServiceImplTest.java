package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.CommentReplySensitiveWordsProperties;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentReplySensitiveWordHitDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentReplySensitiveWordServiceImplTest {

    @Test
    void detectsEnabledWordsAfterNormalizationAndIgnoresDisabledWords() {
        MtProperties properties = new MtProperties();
        CommentReplySensitiveWordsProperties wordsProperties = new CommentReplySensitiveWordsProperties();
        CommentReplySensitiveWordsProperties.Category category = new CommentReplySensitiveWordsProperties.Category();
        category.setCode("OFF_PLATFORM");
        category.setName("站外导流违规词");
        category.setSeverity("HIGH");
        CommentReplySensitiveWordsProperties.Word enabled = new CommentReplySensitiveWordsProperties.Word();
        enabled.setText("微信");
        CommentReplySensitiveWordsProperties.Word disabled = new CommentReplySensitiveWordsProperties.Word();
        disabled.setText("支付宝");
        disabled.setEnabled(false);
        category.getWords().add(enabled);
        category.getWords().add(disabled);
        wordsProperties.getCategories().add(category);

        CommentReplySensitiveWordServiceImpl service = new CommentReplySensitiveWordServiceImpl(properties, wordsProperties);

        CommentReplySensitiveWordHitDTO hit = service.detect("请加 微-信 处理");
        assertTrue(hit.isHit());
        assertEquals("HIGH", hit.getHighestSeverity());
        assertEquals("站外导流违规词", hit.getCategories().get(0));

        assertFalse(service.detect("支持支付宝").isHit());
    }
}
