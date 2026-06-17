package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台有赞订购订单列表行
 */
@Data
public class AdminYouzanAppOrderRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNo;

    private Long kdtId;

    private Long rootKdtId;

    private Long appId;

    private Long buyerId;

    private String buyerPhone;

    private String openId;

    private String shopName;

    private String shopDisplayNo;

    private String skuVersionText;

    private String skuIntervalText;

    private Integer price;

    private Integer status;

    private LocalDateTime payTime;

    private String useStatus;

    private Long usedMtShopId;

    private String usedMtShopName;

    private String usedRightCode;

    private LocalDateTime usedAt;

    private String usedByUsername;

    private String useRemark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
