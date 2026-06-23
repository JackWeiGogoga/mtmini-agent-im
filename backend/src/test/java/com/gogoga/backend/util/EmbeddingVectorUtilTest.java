package com.gogoga.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingVectorUtilTest {

    @Test
    void encodesAndDecodesFloat32LittleEndianBlob() {
        float[] vector = new float[]{1.25F, -2.5F, 0F, 3.75F};

        byte[] blob = EmbeddingVectorUtil.toFloat32Blob(vector);
        float[] decoded = EmbeddingVectorUtil.fromFloat32Blob(blob);

        assertEquals(vector.length * Float.BYTES, blob.length);
        assertArrayEquals(vector, decoded);
    }

    @Test
    void rejectsInvalidBlobLength() {
        assertThrows(IllegalArgumentException.class, () -> EmbeddingVectorUtil.fromFloat32Blob(new byte[]{1, 2, 3}));
    }

    @Test
    void calculatesCosineSimilarityAndHandlesInvalidInputs() {
        assertEquals(1D, EmbeddingVectorUtil.cosineSimilarity(new float[]{1F, 0F}, new float[]{1F, 0F}), 0.000001D);
        assertEquals(0D, EmbeddingVectorUtil.cosineSimilarity(new float[]{1F, 0F}, new float[]{0F, 1F}), 0.000001D);
        assertEquals(0D, EmbeddingVectorUtil.cosineSimilarity(new float[]{1F}, new float[]{1F, 1F}), 0.000001D);
        assertEquals(0D, EmbeddingVectorUtil.cosineSimilarity(new float[]{0F, 0F}, new float[]{1F, 1F}), 0.000001D);
    }
}
