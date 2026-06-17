package com.gogoga.backend.entity.youzan;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 有赞店铺实体
 */
@Data
public class YzShop {

    private Long id;

    private Long kdtId;

    private Long rootKdtId;

    private Long appId;

    private String shopName;

    private String shopDisplayNo;

    private String shopType;

    private String shopRole;

    private Long buyerId;

    private String buyerPhone;

    private String openId;

    private String env;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
