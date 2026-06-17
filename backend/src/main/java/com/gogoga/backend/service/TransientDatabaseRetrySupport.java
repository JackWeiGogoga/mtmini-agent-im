package com.gogoga.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;

/**
 * 数据库瞬态锁冲突重试辅助。
 */
@Slf4j
final class TransientDatabaseRetrySupport {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 50L;

    private TransientDatabaseRetrySupport() {
    }

    static void run(String operationName, String bizKey, Runnable action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                action.run();
                return;
            } catch (TransientDataAccessException e) {
                lastException = e;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
                long backoffMillis = INITIAL_BACKOFF_MILLIS * attempt;
                log.warn("数据库瞬态错误，准备重试，operation={}, bizKey={}, attempt={}, nextBackoffMillis={}, error={}",
                        operationName, bizKey, attempt, backoffMillis, e.getMessage());
                sleep(backoffMillis);
            }
        }
        throw lastException;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DATABASE_TRANSIENT_RETRY_INTERRUPTED", e);
        }
    }
}
