package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店 IM 自动回复配置。
 */
@Data
public class MtImAutoReplySetting {

    private Long id;
    private Long shopId;
    private Boolean autoReplyEnabled;
    /** 商家自定义联系方式，优先于门店电话拼接在自动回复结尾 */
    private String customContactPhone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
