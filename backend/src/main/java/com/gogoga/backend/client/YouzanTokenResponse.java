package com.gogoga.backend.client;

import lombok.Data;

/**
 * 有赞 token 接口解析结果
 */
@Data
public class YouzanTokenResponse {

    private boolean success;

    private String code;

    private String message;

    private String accessToken;

    private String refreshToken;

    private String scope;

    private Long expiresIn;

    private Long expiresAtMillis;

    private Long refreshExpiresIn;

    private Long refreshExpiresAtMillis;

    private String authorityId;

    private Long kdtId;

    private String rawResponseJson;
}
