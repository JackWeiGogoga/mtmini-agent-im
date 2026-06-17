package com.gogoga.backend.vo;

import lombok.Data;

@Data
public class SalesSelfConfigVO {

    private String salesUserId;

    private String salesUserName;

    private String salesUserPhone;

    private String redemptionCode;

    private String receiptCodeUrl;

    private Boolean receiptCodeUploadAllowed;
}
