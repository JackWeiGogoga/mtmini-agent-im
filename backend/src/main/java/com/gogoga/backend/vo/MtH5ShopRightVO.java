package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * H5 门店权益信息
 *
 * @author system
 * @since 2026-03-25
 */
@Data
public class MtH5ShopRightVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权益编码
     */
    private String serviceCode;

    /**
     * 权益类型
     */
    private Integer type;

    /**
     * 是否生效
     */
    private Boolean active;

    /**
     * 到期时间
     */
    private LocalDateTime expireAt;

    /**
     * 来源版本
     */
    private String sourceVersion;
}
