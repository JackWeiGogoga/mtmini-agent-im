package com.gogoga.backend.entity.youzan;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 有赞回调事件实体
 */
@Data
public class YzCallbackEvent {

    private Long id;

    private String eventType;

    private String requestUri;

    private String rawQueryString;

    private String messageCiphertext;

    private String authCode;

    private String decodedPayloadJson;

    private String processStatus;

    private String errorMsg;

    private String clientIp;

    private String userAgent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
