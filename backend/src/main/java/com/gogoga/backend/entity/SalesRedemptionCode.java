package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesRedemptionCode {

    private Long id;

    private String code;

    private String salesUserId;

    private String salesUserPhone;

    private Integer status;

    private String createdByUserId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
