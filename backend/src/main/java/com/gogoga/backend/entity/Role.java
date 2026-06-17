package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体类
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class Role {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 角色ID
     */
    private String roleId;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer orderNum;

    /**
     * 状态 0:禁用 1:正常
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
