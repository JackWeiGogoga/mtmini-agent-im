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

    private Boolean negativeCommentAppealEnabled;
}
