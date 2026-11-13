package com.gogoga.backend.entity.sms;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店应用权益最大到期时间投影。
 */
@Data
public class SmsAppExpiryCandidate {

    private Long shopId;
    private LocalDateTime expectedExpireAt;
}
