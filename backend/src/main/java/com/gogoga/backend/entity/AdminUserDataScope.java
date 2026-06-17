package com.gogoga.backend.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台用户数据范围实体
 */
@Data
public class AdminUserDataScope implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String userId;

    private String scopeType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
