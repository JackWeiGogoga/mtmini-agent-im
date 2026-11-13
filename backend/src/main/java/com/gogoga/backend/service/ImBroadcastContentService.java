package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.vo.ImBroadcastContentOptimizeVO;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * AI 群发文案优化：复用 IM 自动回复的模型通道，命中敏感词自动改写一次，产物按算力实际 token 计费。
 *
 * <p>两个提示词均托管在管理后台「系统提示词」：主提示词 {@link #PROMPT_KEY}、改写提示词 {@link #REWRITE_PROMPT_KEY}，
 * 变量 {@code {{max_length}}} 由渲染时替换为文案上限字数。库表与资源都缺失时回落到 {@code MtProperties} 的代码兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImBroadcastContentService {

    private static final String POWER_SOURCE_TYPE = "IM_BROADCAST_CONTENT_OPTIMIZE";
    private static final String PROMPT_KEY = "IM_BROADCAST_CONTENT_OPTIMIZE";
    private static final String REWRITE_PROMPT_KEY = "IM_BROADCAST_CONTENT_OPTIMIZE_REWRITE";
    private static final String VAR_MAX_LENGTH = "max_length";

    private final MtProperties mtProperties;
    private final PowerProperties powerProperties;
    private final MtShopRightService shopRightService;
    private final OpenAiCompatibleChatService chatService;
    private final SensitiveWordService sensitiveWordService;
    private final MtPowerConsumeService powerConsumeService;
    private final SystemPromptService systemPromptService;

    /**
     * @param shopId  发起门店，用于权益校验与算力扣减
     * @param content 文本框当前内容
     * @return 优化后的合规文案
     */
    public ImBroadcastContentOptimizeVO optimize(Long shopId, String content) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        // AI 优化属于群发能力，只有回复权益的门店不得调用，避免白嫖大模型。
        if (!shopRightService.hasBroadcastRight(shopId)) {
            throw new BusinessException("当前版本不支持消息群发，请升级后使用");
        }
        String source = StringUtils.trim(content);
        if (StringUtils.isBlank(source)) {
            throw new BusinessException("请先填写要优化的内容");
        }

        MtProperties.ContentOptimize config = mtProperties.getImBroadcast().getContentOptimize();
        int maxLength = Math.max(mtProperties.getImBroadcast().getMaxContentLength(), 1);
        Map<String, Object> variables = Map.of(VAR_MAX_LENGTH, maxLength);
        // 提示词优先取后台配置，其次资源文件，最后才是代码兜底；变量在渲染阶段统一替换。
        String systemPrompt = systemPromptService.render(PROMPT_KEY, variables, config.getSystemPrompt()).getContent();

        // 首轮优化。
        LlmOutcome first = invoke(systemPrompt, source, null);
        long promptTokens = first.promptTokens();
        long completionTokens = first.completionTokens();
        String optimized = first.content();

        // 命中敏感词则自动改写一次，两轮 token 一并计费。
        SensitiveWordHitDTO hit = sensitiveWordService.detect(optimized);
        if (hit != null && hit.isHit()) {
            String rewriteHint = systemPromptService.render(
                    REWRITE_PROMPT_KEY, variables, config.getSensitiveRewriteHint()).getContent();
            LlmOutcome second = invoke(systemPrompt, source, rewriteHint);
            promptTokens += second.promptTokens();
            completionTokens += second.completionTokens();
            optimized = second.content();
            SensitiveWordHitDTO retryHit = sensitiveWordService.detect(optimized);
            if (retryHit != null && retryHit.isHit()) {
                // 两轮仍不合规，先把已发生的模型成本落账，再让商家手动调整。
                bill(shopId, promptTokens, completionTokens);
                throw new BusinessException("AI 优化后的文案仍包含敏感信息，请手动调整后再试");
            }
        }

        // 模型偶发超长，硬截断兜底，保证产物一定能通过群发保存的字数校验。
        optimized = truncate(optimized, maxLength);
        bill(shopId, promptTokens, completionTokens);

        ImBroadcastContentOptimizeVO vo = new ImBroadcastContentOptimizeVO();
        vo.setContent(optimized);
        log.info("群发文案AI优化完成，shopId={}, promptTokens={}, completionTokens={}, length={}",
                shopId, promptTokens, completionTokens, optimized.length());
        return vo;
    }

    private LlmOutcome invoke(String systemPrompt, String source, String rewriteHint) {
        MtProperties.ImAutoReply model = mtProperties.getImAutoReply();
        MtProperties.ContentOptimize config = mtProperties.getImBroadcast().getContentOptimize();
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model.getChatModel())
                .temperature(config.getTemperature())
                .maxCompletionTokens(Math.max(config.getMaxCompletionTokens(), 1))
                .addSystemMessage(systemPrompt)
                .addUserMessage(source);
        if (StringUtils.isNotBlank(rewriteHint)) {
            builder.addSystemMessage(rewriteHint);
        }
        ChatCompletion completion = chatService.createChatCompletion(
                model.getModelProvider(), model.getModelBaseUrl(), model.getModelApiKey(), builder.build());
        return parse(completion);
    }

    private LlmOutcome parse(ChatCompletion completion) {
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new BusinessException(502, "文案优化服务响应缺少候选结果");
        }
        String text = completion.choices().get(0).message().content().orElse(null);
        if (StringUtils.isBlank(text)) {
            throw new BusinessException(502, "文案优化服务响应正文为空");
        }
        CompletionUsage usage = completion.usage().orElseThrow(
                () -> new BusinessException(502, "文案优化服务响应缺少 token 用量"));
        return new LlmOutcome(sanitize(text), safeLong(usage.promptTokens()), safeLong(usage.completionTokens()));
    }

    private void bill(Long shopId, long promptTokens, long completionTokens) {
        if (promptTokens <= 0L && completionTokens <= 0L) {
            // 理论上不会发生：模型有响应必有用量。缺用量则不硬造计费，避免抛异常吞掉已成功的优化结果。
            log.warn("群发文案AI优化缺少 token 用量，跳过计费，shopId={}", shopId);
            return;
        }
        int powerAmount = powerProperties.getBilling().calculateLlmPowerAmount(promptTokens, completionTokens);
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(POWER_SOURCE_TYPE);
        // 每次优化都是独立可计费动作，用 UUID 作幂等号，天然不与历史请求碰撞。
        // source_id / request_no 均为 varchar(64)，来源类型已由 source_type 列承载，这里不再带前缀，避免超长截断。
        String requestNo = UUID.randomUUID().toString();
        request.setSourceId(requestNo);
        request.setRequestNo(requestNo);
        request.setPowerAmount(powerAmount);
        request.setRemark("群发文案AI优化模型实际token计费");
        powerConsumeService.consume(request);
    }

    private String truncate(String text, int maxLength) {
        String trimmed = StringUtils.trim(text);
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    /**
     * 剥掉模型偶尔多包的成对引号，避免把 "文案" 这种带引号的结果原样塞回文本框。
     */
    private String sanitize(String text) {
        String trimmed = StringUtils.trim(text);
        if (trimmed.length() >= 2) {
            char head = trimmed.charAt(0);
            char tail = trimmed.charAt(trimmed.length() - 1);
            if ((head == '"' && tail == '"') || (head == '“' && tail == '”')) {
                trimmed = StringUtils.trim(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return trimmed;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private record LlmOutcome(String content, long promptTokens, long completionTokens) {
    }
}
