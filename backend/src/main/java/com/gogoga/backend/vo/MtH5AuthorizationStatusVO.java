package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * H5 美团授权状态视图。
 */
@Data
public class MtH5AuthorizationStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_REAUTH_REQUIRED = "REAUTH_REQUIRED";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    private String status;

    private Integer authStatus;

    private Boolean reauthRequired;

    private String message;

    private LocalDateTime authStatusChangedAt;

    private LocalDateTime lastAuthFailureAt;
}
