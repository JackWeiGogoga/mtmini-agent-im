package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体类
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class UserRole {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 角色ID
     */
    private String roleId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
