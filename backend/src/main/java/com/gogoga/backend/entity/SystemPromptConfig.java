package com.gogoga.backend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统提示词配置。
 */
@Data
public class SystemPromptConfig {

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
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
