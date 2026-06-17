package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserBehaviorEvent {

    private Long id;

    private String eventSource;

    private String eventType;

    private Long shopId;

    private String shopNameSnapshot;

    private String shopPhoneSnapshot;

    private String backendUserId;

    private String h5EpoiId;

    private LocalDateTime eventTime;

    private String contextJson;

    private LocalDateTime createTime;
}
