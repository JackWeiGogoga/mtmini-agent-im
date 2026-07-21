package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 门店操作开关视图。
 */
@Data
public class ShopOperationSettingsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long shopId;

    private Boolean autoReplyEnabled;

    /**
     * 商家自定义联系方式，为空表示未配置（回退门店电话）。
     */
    private String customContactPhone;

    /**
     * 实际生效的联系方式：自定义 &gt; 门店电话 &gt; 门店联系方式，都没有时为空。
     */
    private String effectiveContactPhone;
}
