package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.constant.SystemPromptKeyConstant;
import com.gogoga.backend.dto.CommentImComplianceResultDTO;
import com.gogoga.backend.dto.CommentImMessageGenerationResultDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.enums.CommentImMessageSourceEnum;
import com.gogoga.backend.service.CommentImMessageComplianceService;
import com.gogoga.backend.service.CommentImMessageGenerationService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.SystemPromptService;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 差评匿名群聊消息生成实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentImMessageGenerationServiceImpl implements CommentImMessageGenerationService {

    private static final String MODEL_PROVIDER = "TOKENHUB";
    private static final int MAX_CODE_POINTS = 150;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern FALLBACK_COMMENT_VARIABLE = Pattern.compile("\\{\\{\\s*commentContent\\s*}}");
    private static final String MISSING_COMMENT_CONTENT = "未填写文字评价";

    private final MtProperties mtProperties;
    private final PowerProperties powerProperties;
    private final SystemPromptService systemPromptService;
    private final OpenAiCompatibleChatService openAiCompatibleChatService;
    private final CommentImMessageComplianceService complianceService;

    @Override
    public CommentImMessageGenerationResultDTO generate(MtComment comment) {
        long startNanos = System.nanoTime();
        ModelResult modelResult = null;
        String fallbackReason = null;
        try {
            modelResult = callModel(comment);
            String cleaned = cleanAndValidateFormat(modelResult.content);
            CommentImComplianceResultDTO compliance = complianceService.validate(cleaned);
            if (!compliance.isPassed()) {
                throw new IllegalStateException("MODEL_COMPLIANCE_" + compliance.getCategory());
            }
            return toModelResult(modelResult, cleaned, durationMs(startNanos));
        } catch (Exception e) {
            fallbackReason = safeReason(e);
            log.warn("差评匿名群聊消息使用固定兜底，commentId={}, reason={}",
                    comment == null ? null : comment.getCommentId(), fallbackReason);
        }

        String fallback = cleanAndValidateFormat(renderFallbackMessage(comment));
        // 顾客评价位于固定引用区，不代表商家承诺；合规检查聚焦模板固定话术。
        String fallbackForCompliance = cleanAndValidateFormat(renderFallbackTemplate("评价内容"));
        CommentImComplianceResultDTO compliance = complianceService.validate(fallbackForCompliance);
        if (!compliance.isPassed()) {
            throw new IllegalStateException("IM_FALLBACK_COMPLIANCE_" + compliance.getCategory());
        }
        CommentImMessageGenerationResultDTO result = new CommentImMessageGenerationResultDTO();
        result.setMessageContent(fallback);
        result.setMessageSource(CommentImMessageSourceEnum.FALLBACK_STATIC.name());
        result.setFallbackReason(fallbackReason);
        if (modelResult != null) {
            result.setModelProvider(MODEL_PROVIDER);
            result.setModel(modelResult.model);
            result.setPromptKey(modelResult.promptKey);
            result.setPromptVersion(modelResult.promptVersion);
            result.setPromptTokens(modelResult.promptTokens);
            result.setCompletionTokens(modelResult.completionTokens);
            result.setTotalTokens(modelResult.totalTokens);
        }
        result.setPowerAmount(Math.max(mtProperties.getComment().getImFollowUp().getFallbackPowerAmount(), 1));
        result.setDurationMs(durationMs(startNanos));
        return result;
    }

    String renderFallbackMessage(MtComment comment) {
        String commentContent = sanitizeFallbackComment(comment == null ? null : comment.getCommentContent());
        return renderFallbackTemplate(commentContent);
    }

    private String renderFallbackTemplate(String commentContent) {
        String template = mtProperties.getComment().getImFollowUp().getFallbackMessage();
        if (StringUtils.isBlank(template) || !FALLBACK_COMMENT_VARIABLE.matcher(template).find()) {
            return template;
        }
        String value = StringUtils.defaultIfBlank(commentContent, MISSING_COMMENT_CONTENT);
        String fullMessage = replaceFallbackVariable(template, value);
        if (codePointCountAfterWhitespaceCleanup(fullMessage) <= MAX_CODE_POINTS) {
            return fullMessage;
        }

        int totalCodePoints = value.codePointCount(0, value.length());
        String best = replaceFallbackVariable(template, "");
        int low = 0;
        int high = Math.max(totalCodePoints - 1, 0);
        while (low <= high) {
            int keepCodePoints = low + (high - low) / 2;
            String shortened = leftCodePoints(value, keepCodePoints) + "…";
            String candidate = replaceFallbackVariable(template, shortened);
            if (codePointCountAfterWhitespaceCleanup(candidate) <= MAX_CODE_POINTS) {
                best = candidate;
                low = keepCodePoints + 1;
            } else {
                high = keepCodePoints - 1;
            }
        }
        return best;
    }

    private String sanitizeFallbackComment(String content) {
        String cleaned = StringUtils.trimToNull(content);
        if (cleaned == null) {
            return MISSING_COMMENT_CONTENT;
        }
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim()
                .replace("```", "｀｀｀")
                .replace("{{", "｛｛")
                .replace("}}", "｝｝")
                .replace("[", "［")
                .replace("]", "］");
        StringBuilder withoutEmoji = new StringBuilder(cleaned.length());
        for (int offset = 0; offset < cleaned.length(); ) {
            int codePoint = cleaned.codePointAt(offset);
            if (!isEmojiCodePoint(codePoint) && codePoint != 0xFE0F && codePoint != 0x200D) {
                withoutEmoji.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return StringUtils.defaultIfBlank(withoutEmoji.toString().trim(), MISSING_COMMENT_CONTENT);
    }

    private String replaceFallbackVariable(String template, String value) {
        return FALLBACK_COMMENT_VARIABLE.matcher(template).replaceAll(java.util.regex.Matcher.quoteReplacement(value));
    }

    private int codePointCountAfterWhitespaceCleanup(String content) {
        String cleaned = WHITESPACE.matcher(StringUtils.defaultString(content)).replaceAll(" ").trim();
        return cleaned.codePointCount(0, cleaned.length());
    }

    private String leftCodePoints(String value, int codePointCount) {
        if (codePointCount <= 0) {
            return "";
        }
        int endIndex = value.offsetByCodePoints(0, Math.min(codePointCount,
                value.codePointCount(0, value.length())));
        return value.substring(0, endIndex);
    }

    String cleanAndValidateFormat(String content) {
        String original = StringUtils.trimToNull(content);
        if (original == null) {
            throw new IllegalStateException("IM_MESSAGE_EMPTY");
        }
        if (original.startsWith("{") || original.startsWith("[") || original.contains("```")
                || original.contains("{{") || original.contains("}}") || original.startsWith("#")) {
            throw new IllegalStateException("IM_MESSAGE_FORMAT_INVALID");
        }
        String cleaned = WHITESPACE.matcher(original).replaceAll(" ").trim();
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_CODE_POINTS) {
            throw new IllegalStateException("IM_MESSAGE_TOO_LONG");
        }
        int emojiCount = countEmoji(cleaned);
        if (emojiCount < 1 || emojiCount > 2) {
            throw new IllegalStateException("IM_MESSAGE_EMOJI_INVALID");
        }
        return cleaned;
    }

    private ModelResult callModel(MtComment comment) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        ResolvedSystemPromptDTO prompt = systemPromptService.render(
                SystemPromptKeyConstant.COMMENT_IM_FOLLOW_UP_MESSAGE,
                buildVariables(comment),
                defaultPrompt()
        );
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(knowledge.getChatModel())
                .addUserMessage(prompt.getContent())
                .build();
        ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(
                knowledge.getModelBaseUrl(), knowledge.getModelApiKey(), params);
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new IllegalStateException("IM_MODEL_EMPTY_RESPONSE");
        }
        ModelResult result = new ModelResult();
        result.content = completion.choices().get(0).message().content().orElse(null);
        result.model = StringUtils.defaultIfBlank(completion.model(), knowledge.getChatModel());
        result.promptKey = prompt.getPromptKey();
        result.promptVersion = prompt.getVersion();
        completion.usage().ifPresent(usage -> fillUsage(result, usage));
        return result;
    }

    private CommentImMessageGenerationResultDTO toModelResult(ModelResult model, String content, int durationMs) {
        CommentImMessageGenerationResultDTO result = new CommentImMessageGenerationResultDTO();
        result.setMessageContent(content);
        result.setMessageSource(CommentImMessageSourceEnum.MODEL.name());
        result.setModelProvider(MODEL_PROVIDER);
        result.setModel(model.model);
        result.setPromptKey(model.promptKey);
        result.setPromptVersion(model.promptVersion);
        result.setPromptTokens(model.promptTokens);
        result.setCompletionTokens(model.completionTokens);
        result.setTotalTokens(model.totalTokens);
        result.setPowerAmount(calculatePower(model));
        result.setDurationMs(durationMs);
        return result;
    }

    private int calculatePower(ModelResult result) {
        try {
            return powerProperties.getBilling().calculateLlmPowerAmount(
                    toLong(result.promptTokens), toLong(result.completionTokens));
        } catch (Exception e) {
            return Math.max(mtProperties.getComment().getImFollowUp().getFallbackPowerAmount(), 1);
        }
    }

    private void fillUsage(ModelResult result, CompletionUsage usage) {
        result.promptTokens = toInt(usage.promptTokens());
        result.completionTokens = toInt(usage.completionTokens());
        result.totalTokens = toInt(usage.totalTokens());
    }

    private Map<String, Object> buildVariables(MtComment comment) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("commentContent", comment == null ? null : comment.getCommentContent());
        variables.put("addComment", comment == null ? null : comment.getAddComment());
        return variables;
    }

    private int countEmoji(String value) {
        int count = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (isEmojiCodePoint(codePoint)) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF);
    }

    private Integer toInt(long value) {
        if (value <= 0L) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private int durationMs(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(millis, 0L);
    }

    private String safeReason(Exception e) {
        return StringUtils.left(StringUtils.defaultIfBlank(e == null ? null : e.getMessage(), "IM_MODEL_FAILED"), 500);
    }

    private String defaultPrompt() {
        return "你是美团外卖商家的资深售后客服，正在评价匿名群聊中首次联系顾客。\n"
                + "顾客评价：{{commentContent}}\n顾客追评：{{addComment}}\n"
                + "请简述反馈、致歉并礼貌索取订单信息和方便联系的手机号码。"
                + "可以使用“核实后给您补偿处理”等明确以核实为前提的条件式补偿表述；"
                + "不得直接或无条件承诺补偿，不得承诺红包、优惠券、退款、赔付、补发或其他具体利益，"
                + "不得引导评价操作、站外导流或索取高风险信息。"
                + "150个Unicode字符以内，单段纯文本，使用1至2个表情，只输出最终消息。";
    }

    private static class ModelResult {
        private String content;
        private String model;
        private String promptKey;
        private Integer promptVersion;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
