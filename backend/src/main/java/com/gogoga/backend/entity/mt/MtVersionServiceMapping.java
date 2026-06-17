package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 美团版本与权益编码映射实体
 *
 * @author system
 * @since 2026-03-25
 */
@Data
public class MtVersionServiceMapping {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 美团版本号
     */
    private String mtVersion;

    /**
     * 权益编码
     */
    private String serviceCode;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 排序值
     */
    private Integer sort;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
