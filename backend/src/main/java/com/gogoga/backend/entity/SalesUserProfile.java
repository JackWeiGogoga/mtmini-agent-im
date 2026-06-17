package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesUserProfile {

    private Long id;

    private String userId;

    private String receiptCodeUrl;

    private String receiptCodeStorageProvider;

    private String receiptCodeObjectKey;

    private LocalDateTime receiptCodeUploadedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
