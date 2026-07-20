package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.enums.KnowledgeCategoryEnum;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 使用大模型对用户消息做粗粒度分类：普通类 / 配送类 / 售后类。
 *
 * <p>分类结果用于知识召回的分类优先与首页售后消息统计；任何异常都兜底为普通类，避免影响回复主流程。</p>
 */
@Service
@RequiredArgsConstructor
public class ImMessageClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ImMessageClassificationService.class);

    private static final String PROMPT_KEY = "IM_MESSAGE_CLASSIFICATION";

    /** 代码兜底：数据库与资源默认均不可用时使用；日常请在管理后台「系统提示词」中维护。 */
    private static final String FALLBACK_SYSTEM_PROMPT = "你是外卖门店客服的消息分类器。阅读顾客消息后，只输出一个英文分类标签，"
            + "不要输出任何解释或标点：\n"
            + "GENERAL=普通咨询（菜品、口味、活动、营业时间、门店信息等一般问题）\n"
            + "DELIVERY=配送相关（催单、配送进度、骑手、送达时间、配送地址等）\n"
            + "AFTER_SALE=售后相关（退款、少送/错送、食品质量、投诉、发票、赔付等）\n"
            + "只能返回 GENERAL、DELIVERY、AFTER_SALE 三者之一。";

    private final MtProperties mtProperties;
    private final OpenAiCompatibleChatService chatService;
    private final SystemPromptService systemPromptService;

    public KnowledgeCategoryEnum classify(List<String> recentUserMessages) {
        return classifyDetailed(recentUserMessages).category();
    }

    /**
     * 分类并返回诊断细节（模型原始输出、prompt 来源、异常信息），用于测试聊天排查分类为何不符合预期。
     */
    public ClassificationResult classifyDetailed(List<String> recentUserMessages) {
        String content = join(recentUserMessages);
        if (StringUtils.isBlank(content)) {
            return new ClassificationResult(KnowledgeCategoryEnum.GENERAL, null, null, "EMPTY_INPUT");
        }
        String promptSource = null;
        try {
            MtProperties.ImAutoReply config = mtProperties.getImAutoReply();
            ResolvedSystemPromptDTO prompt = systemPromptService.resolve(PROMPT_KEY, FALLBACK_SYSTEM_PROMPT);
            promptSource = prompt.getSource();
            // 预算需覆盖“思考型”模型的推理 token，过小会出现 content 为空（被截断）而无异常，
            // 从而恒兜底为普通类；此处与回复生成保持一致的预算，避免分类被截断。
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(config.getChatModel())
                    .temperature(0D)
                    .maxCompletionTokens(Math.max(config.getMaxCompletionTokens(), 64))
                    .addSystemMessage(prompt.getContent())
                    .addUserMessage(content)
                    .build();
            ChatCompletion completion = chatService.createChatCompletion(
                    config.getModelProvider(), config.getModelBaseUrl(), config.getModelApiKey(), params);
            String raw = extractContent(completion);
            return new ClassificationResult(resolve(raw), raw, promptSource, null);
        } catch (Exception e) {
            log.warn("IM 消息分类失败，兜底为普通类: {}", e.getMessage());
            return new ClassificationResult(KnowledgeCategoryEnum.GENERAL, null, promptSource,
                    e.getClass().getSimpleName() + ": " + StringUtils.defaultString(e.getMessage()));
        }
    }

    /**
     * @param category    最终分类
     * @param rawOutput   模型原始返回（兜底时为 null）
     * @param promptSource 提示词来源 DB/RESOURCE/FALLBACK
     * @param error       异常摘要（正常为 null）
     */
    public record ClassificationResult(KnowledgeCategoryEnum category, String rawOutput,
                                       String promptSource, String error) {
    }

    private String join(List<String> recentUserMessages) {
        if (recentUserMessages == null) {
            return "";
        }
        return recentUserMessages.stream()
                .map(StringUtils::trimToNull)
                .filter(value -> value != null)
                .collect(Collectors.joining("\n"));
    }

    private String extractContent(ChatCompletion completion) {
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            return null;
        }
        return completion.choices().get(0).message().content().orElse(null);
    }

    /**
     * 容忍模型返回英文标签或中文措辞，无法识别时兜底普通类。
     */
    private KnowledgeCategoryEnum resolve(String raw) {
        if (StringUtils.isBlank(raw)) {
            return KnowledgeCategoryEnum.GENERAL;
        }
        String upper = raw.toUpperCase();
        if (upper.contains("DELIVERY") || raw.contains("配送")) {
            return KnowledgeCategoryEnum.DELIVERY;
        }
        if (upper.contains("AFTER") || raw.contains("售后")) {
            return KnowledgeCategoryEnum.AFTER_SALE;
        }
        return KnowledgeCategoryEnum.GENERAL;
    }
}
