package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店知识条目视图。
 */
@Data
public class KnowledgeItemVO {

    private Long id;
    private Long shopId;
    private String title;
    private String question;
    private String answer;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
