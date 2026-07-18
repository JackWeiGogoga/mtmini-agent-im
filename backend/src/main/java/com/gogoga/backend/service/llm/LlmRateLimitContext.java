package com.gogoga.backend.service.llm;

import java.util.function.Supplier;

/**
 * 标记同步人工请求，使限流器使用较短最大等待时间。
 */
public final class LlmRateLimitContext {

    private static final ThreadLocal<Boolean> INTERACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private LlmRateLimitContext() {
    }

    public static boolean isInteractive() {
        return Boolean.TRUE.equals(INTERACTIVE.get());
    }

    public static <T> T interactive(Supplier<T> action) {
        Boolean previous = INTERACTIVE.get();
        INTERACTIVE.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (Boolean.TRUE.equals(previous)) {
                INTERACTIVE.set(Boolean.TRUE);
            } else {
                INTERACTIVE.remove();
            }
        }
    }
}
