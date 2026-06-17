package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 美团 token 历史记录。
 */
@Data
public class MtAccessTokenHistory {

    private Long id;

    private Long shopId;

    private String opBizCode;

    private String eventType;

    private String eventStatus;

    private String accessTokenCipher;

    private String refreshTokenCipher;

    private String accessTokenFingerprint;

    private String refreshTokenFingerprint;

    private LocalDateTime expiresAt;

    private Integer responseCode;

    private String responseMsg;

    private String failureReason;

    private String traceId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
