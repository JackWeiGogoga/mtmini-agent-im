package com.gogoga.backend.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtCommentSensitiveTypeEnumTest {

    @Test
    void onlyKnownTypesAreProcessable() {
        assertTrue(MtCommentSensitiveTypeEnum.isProcessable(4));
        assertTrue(MtCommentSensitiveTypeEnum.isProcessable(5));
        assertTrue(MtCommentSensitiveTypeEnum.isProcessable(10));
        assertFalse(MtCommentSensitiveTypeEnum.isProcessable(3));
        assertFalse(MtCommentSensitiveTypeEnum.isProcessable(null));
    }

    @Test
    void contentVisibilityAndRatingPoliciesMatchMeituanSemantics() {
        assertTrue(MtCommentSensitiveTypeEnum.isContentVisible(4));
        assertTrue(MtCommentSensitiveTypeEnum.isRated(4));
        assertFalse(MtCommentSensitiveTypeEnum.isContentVisible(5));
        assertTrue(MtCommentSensitiveTypeEnum.isRated(5));
        assertTrue(MtCommentSensitiveTypeEnum.isContentVisible(10));
        assertFalse(MtCommentSensitiveTypeEnum.isRated(10));
    }
}
