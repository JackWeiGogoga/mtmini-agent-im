package com.gogoga.backend.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台用户上下级关系实体
 */
@Data
public class AdminUserRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String userId;

    private String managerUserId;

    private String relationType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
