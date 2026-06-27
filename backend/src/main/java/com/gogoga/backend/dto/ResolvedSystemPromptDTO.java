package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 运行时解析后的系统提示词。
 */
@Data
public class ResolvedSystemPromptDTO {

    private String promptKey;
    private String content;
    private Integer version;
    private String source;
}
