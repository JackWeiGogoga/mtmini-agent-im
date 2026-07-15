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
    void bindsFallbackTemplateWithCommentVariableFromApplicationYaml() {
        contextRunner.run(context -> assertThat(context.getBean(MtProperties.class)
                .getComment().getImFollowUp().getFallbackMessage())
                .contains("[{{commentContent}}]")
                .contains("核实后给您补偿处理"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MtProperties.class)
    static class TestConfiguration {
    }
}
