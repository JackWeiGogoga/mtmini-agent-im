package com.gogoga.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用敏感词检测结果。不含命中处的原始正文；命中的具体词条（{@link #hitWords}）仅用于后台排查，
 * 面向终端用户的提示请使用 {@code toSafeMessage} 的类别级信息，不要外泄具体词条。
 */
@Data
public class SensitiveWordHitDTO {

    private boolean hit;
    private List<String> categories = new ArrayList<>();
    private List<String> hitWords = new ArrayList<>();
    private Integer hitCount = 0;
    private String highestSeverity;
}
