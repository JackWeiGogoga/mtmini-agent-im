package com.gogoga.backend.entity.youzan;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 有赞应用授权 token 实体
 */
@Data
public class YzAppToken {

    private Long id;

    private Long yzShopId;

    private Long kdtId;

    private String authorityId;

    private String accessToken;

    private String refreshToken;

    private String scope;

    private Long expiresIn;

    private LocalDateTime expiresAt;

    private Long refreshExpiresIn;

    private LocalDateTime refreshExpiresAt;

    private String status;

    private LocalDateTime lastRefreshAt;

    private String rawResponseJson;

    private String lastErrorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
