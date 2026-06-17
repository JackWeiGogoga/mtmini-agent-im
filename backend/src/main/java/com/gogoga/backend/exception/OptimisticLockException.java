package com.gogoga.backend.exception;

import lombok.Getter;

/**
 * 乐观锁冲突异常类
 *
 * @author JackWei
 * @since 2026-01-21
 */
@Getter
public class OptimisticLockException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public OptimisticLockException() {
        super(409, "数据已被修改，请刷新后重试");
    }

    public OptimisticLockException(String message) {
        super(409, message);
    }
}
