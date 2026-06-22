package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店评价自动回复配置。
 */
@Data
public class MtCommentReplySetting {

    private Long id;

    private Long shopId;

    private Boolean autoReplyEnabled;

    private Boolean negativeCommentAppealEnabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
