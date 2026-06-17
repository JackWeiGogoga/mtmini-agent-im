package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 后台算力账户摘要视图
 */
@Data
public class AdminPowerAccountSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long shopId;

    private String poiId;

    private String epoiId;

    private String shopName;

    private String address;

    private String boundUserId;

    private String boundUserName;

    private String bindingStatus;

    private String bindingStatusName;

    private Long ordinaryBindingId;

    private String ordinaryBoundUserId;

    private String ordinaryBoundUserName;

    private Long salesBindingId;

    private String salesBoundUserId;

    private String salesBoundUserName;

    private Integer balance;

    private Integer rechargeBalance;

    private Integer grantBalance;

    private Integer totalRecharge;

    private Integer totalGrant;

    private Integer totalConsume;

    private String riskStatus;
}
