package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论回复知识条目视图。
 */
@Data
public class CommentReplyKnowledgeItemVO {

    private Long id;
    private Long shopId;
    private String scope;
    private String sourceType;
    private String sentiment;
    private String title;
    private String question;
    private String answer;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
