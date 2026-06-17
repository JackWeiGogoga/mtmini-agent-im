package com.gogoga.backend.entity.youzan;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 有赞应用订购订单实体
 */
@Data
public class YzAppOrder {

    private Long id;

    private String orderNo;

    private Long yzShopId;

    private Long kdtId;

    private Long rootKdtId;

    private Long appId;

    private Long buyerId;

    private String buyerPhone;

    private String openId;

    private String shopName;

    private String shopDisplayNo;

    private String shopType;

    private String shopRole;

    private String skuVersionText;

    private String skuIntervalText;

    private Integer price;

    private Integer status;

    private Long payTime;

    private LocalDateTime payTimeAt;

    private String env;

    private String useStatus;

    private Long usedMtShopId;

    private String usedRightCode;

    private LocalDateTime usedAt;

    private String usedByUserId;

    private String usedByUsername;

    private String useRemark;

    private String rawPayloadJson;

    private Long callbackEventId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
