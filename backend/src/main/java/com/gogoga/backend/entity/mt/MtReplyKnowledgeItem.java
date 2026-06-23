package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论回复知识条目。
 */
@Data
public class MtReplyKnowledgeItem {

    private Long id;
    private Long shopId;
    private String scope;
    private String sourceType;
    private String sentiment;
    private String title;
    private String question;
    private String answer;
    private Boolean enabled;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
