package com.gogoga.backend.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 向量编码与相似度工具。
 */
public final class EmbeddingVectorUtil {

    private EmbeddingVectorUtil() {
    }

    public static byte[] toFloat32Blob(float[] vector) {
        if (vector == null || vector.length == 0) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static float[] fromFloat32Blob(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return new float[0];
        }
        if (blob.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("embedding blob length invalid");
        }
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[blob.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    public static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0D;
        }
        double dot = 0D;
        double normLeft = 0D;
        double normRight = 0D;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            normLeft += left[i] * left[i];
            normRight += right[i] * right[i];
        }
        if (normLeft == 0D || normRight == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }
}
