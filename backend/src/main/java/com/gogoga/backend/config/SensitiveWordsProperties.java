package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用敏感词分类词库配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sensitive-words")
public class SensitiveWordsProperties {

    private List<Category> categories = new ArrayList<>();

    @Data
    public static class Category {

        private String code;
        private String name;
        private String severity = "MEDIUM";
        private boolean enabled = true;
        private List<Word> words = new ArrayList<>();
    }

    @Data
    public static class Word {

        private String text;
        private String severity;
        private boolean enabled = true;
    }
}
