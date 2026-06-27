package com.gogoga.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论回复敏感词检测结果。
 */
@Data
public class CommentReplySensitiveWordHitDTO {

    private boolean hit;
    private List<String> categories = new ArrayList<>();
    private Integer hitCount = 0;
    private String highestSeverity;
}
