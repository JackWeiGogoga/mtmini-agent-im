package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店知识条目。
 */
@Data
public class MtKnowledgeItem {

    private Long id;
    private Long shopId;
    private String title;
    private String question;
    private String answer;
    private Boolean enabled;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
