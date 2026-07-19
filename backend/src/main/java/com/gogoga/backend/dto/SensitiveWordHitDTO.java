package com.gogoga.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用敏感词检测结果，不包含命中的具体词条或原始正文。
 */
@Data
public class SensitiveWordHitDTO {

    private boolean hit;
    private List<String> categories = new ArrayList<>();
    private Integer hitCount = 0;
    private String highestSeverity;
}
