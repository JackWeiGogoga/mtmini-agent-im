package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台美团订阅订单列表行视图
 */
@Data
public class AdminMtAppOrderRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long shopId;

    private String poiId;

    private String orderId;

    private String orderViewId;

    private String appId;

    private String serviceCode;

    private Integer payStatus;

    private String version;

    private String versionName;

    private Integer days;

    private String rightStatus;

    private Integer realPayPrice;

    private Integer type;

    private String address;

    private String shopName;

    private String phone;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime orderTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
