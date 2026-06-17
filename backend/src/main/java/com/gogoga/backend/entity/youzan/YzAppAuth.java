package com.gogoga.backend.entity.youzan;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 有赞应用授权消息实体
 */
@Data
public class YzAppAuth {

    private Long id;

    private String orderNo;

    private Long yzShopId;

    private Long kdtId;

    private Long appId;

    private Long buyerId;

    private String buyerPhone;

    private String openId;

    private String shopDisplayNo;

    private String shopType;

    private String shopRole;

    private Long effectTime;

    private LocalDateTime effectTimeAt;

    private Long expireTime;

    private LocalDateTime expireTimeAt;

    private Long payTime;

    private LocalDateTime payTimeAt;

    private String env;

    private String rawPayloadJson;

    private Long callbackEventId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
