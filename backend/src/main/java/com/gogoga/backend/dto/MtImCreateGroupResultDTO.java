package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 美团创建评价联系群结果。
 */
@Data
public class MtImCreateGroupResultDTO {
    private Long groupId;
    private String newGroup;
    private Long ctime;
    private Integer state;
    private String closeType;
    private String responseSummary;
}
