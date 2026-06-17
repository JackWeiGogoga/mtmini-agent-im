package com.gogoga.backend.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminSalesRedemptionCodeRowVO {

    private Long id;

    private String code;

    private String salesUserId;

    private String salesUserName;

    private String salesUserPhone;

    private String receiptCodeUrl;

    @JsonIgnore
    private String receiptCodeObjectKey;

    private LocalDateTime createTime;
}
