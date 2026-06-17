package com.gogoga.backend.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台用户门店绑定实体
 */
@Data
public class UserShopBinding implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String userId;

    private Long shopId;

    private String poiId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
