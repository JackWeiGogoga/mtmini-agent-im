package com.gogoga.backend.service.impl;

import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.SystemPromptConfig;
import com.gogoga.backend.mapper.SystemPromptConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemPromptServiceImplTest {

    private final SystemPromptConfigMapper mapper = mock(SystemPromptConfigMapper.class);
    private final ResourceLoader resourceLoader = mock(ResourceLoader.class);
    private final SystemPromptServiceImpl service = new SystemPromptServiceImpl(mapper, resourceLoader);

    @Test
    void resolveUsesEnabledDatabaseConfig() {
        SystemPromptConfig config = new SystemPromptConfig();
        config.setPromptKey("TEST_PROMPT");
        config.setContent("数据库提示词");
        config.setEnabled(true);
        config.setVersion(3);
        when(mapper.selectByPromptKey("TEST_PROMPT")).thenReturn(config);

        ResolvedSystemPromptDTO result = service.resolve("TEST_PROMPT", "兜底提示词");

        assertThat(result.getContent()).isEqualTo("数据库提示词");
        assertThat(result.getVersion()).isEqualTo(3);
        assertThat(result.getSource()).isEqualTo("DB");
    }

    @Test
    void resolveFallsBackToResourceWhenConfigDisabled() {
        SystemPromptConfig config = new SystemPromptConfig();
        config.setPromptKey("TEST_PROMPT");
        config.setContent("停用提示词");
        config.setEnabled(false);
        config.setVersion(2);
        config.setDefaultResourcePath("prompts/test.prompt");
        when(mapper.selectByPromptKey("TEST_PROMPT")).thenReturn(config);
        when(resourceLoader.getResource("classpath:prompts/test.prompt"))
                .thenReturn(new ByteArrayResource("资源提示词".getBytes(StandardCharsets.UTF_8)));

        ResolvedSystemPromptDTO result = service.resolve("TEST_PROMPT", "兜底提示词");

        assertThat(result.getContent()).isEqualTo("资源提示词");
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getSource()).isEqualTo("RESOURCE");
    }

    @Test
    void renderReplacesVariablesAndMissingValues() {
        SystemPromptConfig config = new SystemPromptConfig();
        config.setPromptKey("TEST_PROMPT");
        config.setContent("消息：{{message}}，标签：{{label}}，未知：{{missing}}");
        config.setEnabled(true);
        config.setVersion(1);
        when(mapper.selectByPromptKey("TEST_PROMPT")).thenReturn(config);

        ResolvedSystemPromptDTO result = service.render("TEST_PROMPT", Map.of("message", "你好", "label", ""), "兜底");

        assertThat(result.getContent()).isEqualTo("消息：你好，标签：无，未知：无");
    }

    @Test
    void resolveUsesFallbackWhenNoConfigOrResource() {
        when(mapper.selectByPromptKey("TEST_PROMPT")).thenReturn(null);

        ResolvedSystemPromptDTO result = service.resolve("TEST_PROMPT", "兜底提示词");

        assertThat(result.getContent()).isEqualTo("兜底提示词");
        assertThat(result.getSource()).isEqualTo("FALLBACK");
    }
}
