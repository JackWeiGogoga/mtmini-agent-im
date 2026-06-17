package com.gogoga.backend.service.promotion;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 轻量 Prompt 模板渲染器
 */
@Component
public class PromptTemplateRenderer {

    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^{}]+}}|\\$\\{[^{}]+}");

    public String render(String resourcePath, Map<String, String> values) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            String template = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
            StringSubstitutor substitutor = new StringSubstitutor(values, "{{", "}}");
            substitutor.setEnableUndefinedVariableException(true);
            String rendered = substitutor.replace(template);
            if (UNRESOLVED_PLACEHOLDER_PATTERN.matcher(rendered).find()) {
                throw new IllegalStateException("Prompt 模板存在未替换占位符");
            }
            return rendered;
        } catch (IOException e) {
            throw new IllegalStateException("读取 Prompt 模板失败", e);
        }
    }
}
