package com.gogoga.backend.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台总控台行视图对象。
 */
@Data
public class AdminMasterConsoleRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long bindingId;
    private Long shopId;
    private String poiId;
    private String epoiId;
    private String shopName;
    private String address;
    private String contactPhone;
    private String boundUserId;
    private String boundUserName;
    private String bindingDomain;
    private String bindingDomainName;
    private String bindingStatus;
    private String bindingStatusName;
    private Long ordinaryBindingId;
    private String ordinaryBoundUserId;
    private String ordinaryBoundUserName;
    private LocalDateTime ordinaryBindTime;
    private Long salesBindingId;
    private String salesBoundUserId;
    private String salesBoundUserName;
    private String salesBoundUserPhone;
    private LocalDateTime salesBindTime;

    private Integer goodCommentCount;
    private Integer previousGoodCommentCount;
    private Integer badCommentCount;
    private Integer previousBadCommentCount;
    private Integer repliedBadCommentCount;
    private BigDecimal badCommentReplyRate;
    private BigDecimal previousBadCommentReplyRate;

    private Integer powerBalance;
    private Integer totalRecharge;
    private Integer totalGrant;
    private Integer totalConsume;
    private Integer currentConsumedPower;
    private Integer previousConsumedPower;

    private Integer authStatus;
    private String authStatusText;
    private Boolean reauthRequired;
    private LocalDateTime authStatusChangedAt;
    private LocalDateTime lastAuthFailureAt;

    private LocalDateTime rightExpireAt;
    private LocalDateTime bindTime;

    @JsonIgnore
    private Integer ordinaryBindingCount;

    @JsonIgnore
    private Integer salesBindingCount;
}
