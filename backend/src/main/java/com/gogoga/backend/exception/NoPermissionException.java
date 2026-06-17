package com.gogoga.backend.exception;

/**
 * 无权限异常
 *
 * @author system
 * @since 2025-01-21
 */
public class NoPermissionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 权限标识
     */
    private String permission;

    public NoPermissionException() {
        super("没有访问权限");
    }

    public NoPermissionException(String message) {
        super(message);
    }

    public NoPermissionException(String permission, String message) {
        super(message);
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
