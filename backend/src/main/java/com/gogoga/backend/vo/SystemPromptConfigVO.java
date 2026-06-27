package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统提示词配置视图。
 */
@Data
public class SystemPromptConfigVO {

    private Long id;
    private String promptKey;
    private String promptName;
    private String promptType;
    private String content;
    private String defaultResourcePath;
    private String variablesJson;
    private String modelHint;
    private Boolean enabled;
    private Integer version;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
