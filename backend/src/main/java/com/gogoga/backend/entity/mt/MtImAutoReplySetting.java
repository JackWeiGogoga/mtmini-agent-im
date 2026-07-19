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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
