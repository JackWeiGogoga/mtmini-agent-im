package com.gogoga.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MtPropertiesConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsGenericKnowledgeAndImConfigurationFromApplicationYaml() {
        contextRunner.run(context -> {
            MtProperties properties = context.getBean(MtProperties.class);
            assertThat(properties.getKnowledge().getDocumentKeyPrefix()).isEqualTo("knowledge/documents");
            assertThat(properties.getIm()).isNotNull();
            assertThat(properties.getSensitiveWord().isEnabled()).isTrue();
            assertThat(properties.getSensitiveWord().getMaxRewriteAttempts()).isEqualTo(1);
            SensitiveWordsProperties sensitiveWords = context.getBean(SensitiveWordsProperties.class);
            assertThat(sensitiveWords.getCategories()).isNotEmpty();
            assertThat(sensitiveWords.getCategories())
                    .anyMatch(category -> "OFF_PLATFORM".equals(category.getCode()));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({MtProperties.class, SensitiveWordsProperties.class})
    static class TestConfiguration {
    }
}
