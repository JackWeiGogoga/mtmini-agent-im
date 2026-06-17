package com.gogoga.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 *
 * @author system
 * @since 2025-01-21
 */
@Data
public class User {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 用户ID（唯一标识）
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 邮箱（邮箱密码登录用户必填，第三方登录可为空）
     */
    private String email;

    /**
     * 手机号（短信验证码登录用户必填）
     */
    private String phone;

    /**
     * 加密密码（BCrypt，仅邮箱密码登录用户有值）
     */
    @JsonIgnore
    private String password;

    /**
     * 主认证方式（OPENID/PASSWORD/SMS/GOOGLE/GITHUB）
     */
    private String authProvider;

    /**
     * 头像URL
     */
    private String avatar;

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
