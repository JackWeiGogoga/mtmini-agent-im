package com.gogoga.backend.entity;

import com.gogoga.backend.enums.BackendPortalType;
import lombok.Data;

/**
 * 后台认证会话信息
 */
@Data
public class AuthSessionInfo {

    private String userId;

    private BackendPortalType activePortal;

    private String impersonatedUserId;

    private BackendPortalType impersonatedPortal;
}
