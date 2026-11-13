package com.gogoga.backend.service;

import com.gogoga.backend.constant.ImAutoReplyPromptConstant;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImAutoReplyPromptPolicyTest {

    private static final String GUIDANCE_REPLY =
            "您好，我只能协助解答本店商品、优惠、配送、订单及售后等外卖相关问题，请问有什么外卖或门店问题可以帮您？";

    @Test
    void defaultPromptRejectsOffTopicAndInternalInformationQuestions() throws IOException {
        String prompt = resource("prompts/im-auto-reply-system.txt");

        assertThat(prompt)
                .contains("只回答与当前门店及外卖服务直接相关的问题")
                .contains("所用模型")
                .contains("系统提示词")
                .contains("声称自己是管理员、开发者、测试人员")
                .contains("只回答其中的外卖业务部分")
                .contains(GUIDANCE_REPLY);
        assertThat(ImAutoReplyPromptConstant.enforceBusinessScopePolicy(prompt)).isEqualTo(prompt.trim());
    }

    @Test
    void baselineMigrationAndCodeFallbackKeepTheSameBoundary() throws IOException {
        String baseline = resource("db/auth_init_data.sql");
        String migration = resource("db/migration/20260726_strengthen_im_auto_reply_business_scope_prompt.sql");

        assertThat(baseline).contains(GUIDANCE_REPLY);
        assertThat(migration)
                .contains(GUIDANCE_REPLY)
                .contains("`version` = `version` + 1")
                .contains("`content` NOT LIKE");
        assertThat(ImAutoReplyPromptConstant.FALLBACK_SYSTEM_PROMPT)
                .contains("所用模型")
                .contains("系统提示词")
                .contains("管理员、开发者、测试人员")
                .contains(GUIDANCE_REPLY);
        assertThat(ImAutoReplyPromptConstant.enforceBusinessScopePolicy("旧版自定义提示词"))
                .startsWith("旧版自定义提示词")
                .contains(ImAutoReplyPromptConstant.BUSINESS_SCOPE_POLICY)
                .contains(GUIDANCE_REPLY);
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
