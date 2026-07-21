package com.gogoga.backend.service;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.client.MtImMessageClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.ImMessageDeliveryStatusEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.enums.ImMessageTypeEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.enums.KnowledgeCategoryEnum;
import com.gogoga.backend.enums.MtImAutoReplyStatusEnum;
import com.gogoga.backend.exception.PowerInsufficientException;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import com.gogoga.backend.mapper.MtImMessageMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtImAutoReplyTaskPayload;
import com.gogoga.backend.util.MeituanAuthFailureUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IM 自动回复的防抖、生成、RAG、安全、计费和幂等发送编排。
 */
@Service
@RequiredArgsConstructor
public class MtImAutoReplyOrchestrationService {

    private static final String PROMPT_KEY = "IM_AUTO_REPLY_SYSTEM";
    private static final String FALLBACK_SYSTEM_PROMPT = "你是门店在线客服，请基于短期会话历史与门店知识片段简洁、礼貌地回复。"
            + "联系方式、营业时间、门店地址、价格与订单进度等信息，只有知识片段中有明确内容时才可回复，"
            + "否则说明没有确切信息并建议联系人工客服，严禁猜测或编造任何可能误导顾客的内容。";

    private final MtProperties mtProperties;
    private final MtImAutoReplyMapper autoReplyMapper;
    private final MtImMessageMapper messageMapper;
    private final MtShopMapper shopMapper;
    private final ShopOperationSettingsService settingsService;
    private final MtShopRightService shopRightService;
    private final MtAccessTokenService accessTokenService;
    private final MtMeituanAuthorizationFailureHandler authorizationFailureHandler;
    private final ImConversationLockService conversationLockService;
    private final ImAutoReplyContextService contextService;
    private final ImMessageClassificationService messageClassificationService;
    private final ImAutoReplyRagService ragService;
    private final ShopBasicInfoPromptService shopBasicInfoPromptService;
    private final ImAutoReplyChatService chatService;
    private final SystemPromptService systemPromptService;
    private final SensitiveWordService sensitiveWordService;
    private final ImReplyContactSuffixService contactSuffixService;
    private final MtImAutoReplyStateService stateService;
    private final MtImMessageClient messageClient;

    public void execute(MtImAutoReplyTaskPayload payload) {
        validatePayload(payload);
        MtImAutoReply initial = autoReplyMapper.selectById(payload.getAutoReplyId());
        if (initial == null) {
            throw new MtAsyncTaskTerminalFailureException("IM_AUTO_REPLY_NOT_FOUND");
        }
        if (isTerminal(initial.getStatus())) {
            return;
        }
        ImConversationLockService.LockHandle lock = conversationLockService.tryAcquire(initial.getConversationId());
        if (lock == null) {
            throw new MtAsyncTaskDryRunException("IM_CONVERSATION_LOCK_BUSY", 1L);
        }
        try (lock) {
            executeWithLock(initial.getId(), lock);
        }
    }

    /**
     * 数据库任务耗尽重试或超时回收进入 DEAD 时，同步自动回复业务状态。
     */
    public void markDeadAfterRetryExhausted(MtImAutoReplyTaskPayload payload) {
        if (payload == null || payload.getAutoReplyId() == null) {
            return;
        }
        MtImAutoReply autoReply = autoReplyMapper.selectById(payload.getAutoReplyId());
        if (autoReply != null && !isTerminal(autoReply.getStatus())) {
            stateService.markStatus(autoReply.getId(), MtImAutoReplyStatusEnum.DEAD, "RETRY_EXHAUSTED");
        }
    }

    private void executeWithLock(Long autoReplyId, ImConversationLockService.LockHandle lock) {
        MtImAutoReply autoReply = autoReplyMapper.selectById(autoReplyId);
        if (autoReply == null || isTerminal(autoReply.getStatus())) {
            return;
        }
        MtImMessage trigger = messageMapper.selectById(autoReply.getTriggerMessageId());
        if (trigger == null) {
            stateService.markStatus(autoReplyId, MtImAutoReplyStatusEnum.DEAD, "TRIGGER_MESSAGE_NOT_FOUND");
            throw new MtAsyncTaskTerminalFailureException("TRIGGER_MESSAGE_NOT_FOUND");
        }

        SkipDecision decision = checkShopReadiness(autoReply.getShopId());
        if (decision != null) {
            stateService.markStatus(autoReplyId, decision.status(), decision.reason());
            return;
        }
        decision = checkNewerMessages(trigger);
        if (decision != null) {
            stateService.markStatus(autoReplyId, decision.status(), decision.reason());
            return;
        }
        // 必须排在 checkNewerMessages 之后：商家在本条之后发言属于「亲自接手本条」，
        // 走上面的 SKIPPED 终态；此处只处理「商家在本条之前说过话」的静默窗口。
        awaitMerchantHoldWindow(trigger);

        if (autoReply.getReplyMessageId() == null) {
            generateAndPersist(autoReply, trigger);
            autoReply = autoReplyMapper.selectById(autoReplyId);
        }
        if (autoReply.getBilledAt() == null) {
            if (usesModel(trigger)) {
                try {
                    autoReply = stateService.bill(autoReplyId);
                } catch (PowerInsufficientException insufficient) {
                    stateService.markStatus(autoReplyId, MtImAutoReplyStatusEnum.SKIPPED,
                            PowerInsufficientException.REASON_CODE);
                    throw new MtAsyncTaskTerminalFailureException(PowerInsufficientException.REASON_CODE);
                }
            } else {
                // 非文本兜底话术没有 token 用量，算力按实际 token 计费，因此这里零消耗直接过账，
                // 只推进状态机，避免按 token 计费的公式拿到 0 用量后抛异常把任务卡死。
                autoReply = stateService.markBilledFree(autoReplyId);
            }
        }
        if (!lock.isValid()) {
            throw new MtAsyncTaskDryRunException("IM_CONVERSATION_LOCK_LOST", 1L);
        }

        decision = checkNewerMessages(trigger);
        if (decision == null) {
            decision = checkShopReadiness(autoReply.getShopId());
        }
        if (decision != null) {
            stateService.markStatus(autoReplyId, decision.status(), decision.reason());
            return;
        }

        MtImMessage replyMessage = messageMapper.selectById(autoReply.getReplyMessageId());
        if (replyMessage == null || StringUtils.isBlank(replyMessage.getMsgContentPlaintext())) {
            stateService.markStatus(autoReplyId, MtImAutoReplyStatusEnum.DEAD, "REPLY_MESSAGE_NOT_FOUND");
            throw new MtAsyncTaskTerminalFailureException("REPLY_MESSAGE_NOT_FOUND");
        }
        awaitSendDelay(autoReply);
        if (ImMessageDeliveryStatusEnum.SENT.name().equals(replyMessage.getDeliveryStatus())) {
            stateService.markSent(autoReplyId);
            return;
        }
        send(autoReply, trigger, replyMessage);
    }

    private void generateAndPersist(MtImAutoReply autoReply, MtImMessage trigger) {
        stateService.markStatus(autoReply.getId(), MtImAutoReplyStatusEnum.GENERATING, null);
        if (!usesModel(trigger)) {
            generateNonTextFallback(autoReply, trigger);
            return;
        }
        ImAutoReplyContextService.Context context = contextService.build(autoReply.getSessionId());
        List<String> recentUserMessages = context.latestUserMessages(
                mtProperties.getImAutoReply().getRagRecentUserMessages());
        String userMessageCategory = messageClassificationService.classify(recentUserMessages).name();
        ImAutoReplyRagService.RagContext ragContext = ragService.retrieve(
                autoReply.getShopId(), recentUserMessages, userMessageCategory);
        MtShop shop = shopMapper.selectById(autoReply.getShopId());
        String shopName = shop == null ? "当前门店"
                : StringUtils.defaultIfBlank(shop.getPoiName(), StringUtils.defaultIfBlank(shop.getName(), "当前门店"));
        ResolvedSystemPromptDTO prompt = systemPromptService.render(
                PROMPT_KEY, Map.of("shop_name", shopName), FALLBACK_SYSTEM_PROMPT);
        String contactPhone = contactSuffixService.resolveContactPhone(autoReply.getShopId());
        String modelContext = shopBasicInfoPromptService.merge(
                shopBasicInfoPromptService.build(shop, StringUtils.isNotBlank(contactPhone)),
                ragContext.promptContext());

        ImAutoReplyChatResult result = chatService.generate(
                prompt.getContent(), modelContext, context.messages());
        String content = result.content();
        int rewriteCount = 0;
        int maxRewriteAttempts = Math.max(mtProperties.getSensitiveWord().getMaxRewriteAttempts(), 0);
        while (isSensitive(content) && rewriteCount < maxRewriteAttempts) {
            ImAutoReplyChatResult rewritten = chatService.rewrite(
                    prompt.getContent(), modelContext, context.messages(), content);
            result = result.plusUsage(rewritten);
            content = result.content();
            rewriteCount++;
        }
        if (isSensitive(content)) {
            content = mtProperties.getImAutoReply().getSafeFallbackReply();
            if (isSensitive(content)) {
                stateService.markStatus(autoReply.getId(), MtImAutoReplyStatusEnum.DEAD, "SAFE_FALLBACK_REJECTED");
                throw new MtAsyncTaskTerminalFailureException("SAFE_FALLBACK_REJECTED");
            }
        }
        content = contactSuffixService.appendPhone(content, contactPhone);
        stateService.saveGenerated(autoReply.getId(), trigger, content, result, ragContext, rewriteCount,
                userMessageCategory);
    }

    /**
     * 图片等非文本消息的固定兜底回复。
     *
     * <p>不做多模态解析、不召回知识、不调用大模型：这类消息没有可用文本，模型只能凭空猜测，
     * 代价与出错风险都不划算。回复内容是固定话术加常规联系方式后缀，其余环节（商家静默窗口、
     * 更新消息顶替、发送延迟、幂等发送）与文本回复完全一致。</p>
     */
    private void generateNonTextFallback(MtImAutoReply autoReply, MtImMessage trigger) {
        String content = mtProperties.getImAutoReply().getNonTextFallbackReply();
        if (isSensitive(content)) {
            // 兜底话术是配置项，可能被改成命中敏感词的内容；此时没有改写余地，只能不发。
            stateService.markStatus(autoReply.getId(), MtImAutoReplyStatusEnum.DEAD, "NON_TEXT_FALLBACK_REJECTED");
            throw new MtAsyncTaskTerminalFailureException("NON_TEXT_FALLBACK_REJECTED");
        }
        content = contactSuffixService.append(autoReply.getShopId(), content);
        stateService.saveGenerated(autoReply.getId(), trigger, content,
                new ImAutoReplyChatResult(content, 0, 0), null, 0,
                KnowledgeCategoryEnum.GENERAL.name());
    }

    /**
     * 本条触发消息是否走大模型生成。只有承载真实话语的文本消息才值得让模型作答。
     */
    private boolean usesModel(MtImMessage trigger) {
        return ImMessageTypeEnum.isConversational(trigger.getMsgType());
    }

    private void send(MtImAutoReply autoReply, MtImMessage trigger, MtImMessage replyMessage) {
        MtAccessToken token = accessTokenService.findByShopId(autoReply.getShopId());
        if (!isUsableToken(token)) {
            MtAccessTokenService.TokenRefreshResult refresh = accessTokenService.refreshTokenForShopWithResult(autoReply.getShopId());
            token = refresh == null ? null : refresh.token();
        }
        if (!isUsableToken(token)) {
            authorizationFailureHandler.handleConfirmedAuthFailure(
                    autoReply.getShopId(), "IM_AUTO_REPLY_SEND", "TOKEN_UNAVAILABLE", null);
            stateService.markStatus(autoReply.getId(), MtImAutoReplyStatusEnum.SKIPPED, "AUTH_REQUIRED");
            throw new MtAsyncTaskTerminalFailureException("AUTH_REQUIRED");
        }

        stateService.markSending(autoReply.getId());
        MtImMessageClient.SendResult result = sendOnce(token.getAccessToken(), autoReply, trigger, replyMessage);
        if (!result.success() && MeituanAuthFailureUtil.isAuthFailure(result.errorCode(), result.responseSummary())) {
            MtAccessTokenService.TokenRefreshResult refresh = accessTokenService.refreshTokenForShopWithResult(autoReply.getShopId());
            MtAccessToken refreshed = refresh == null ? null : refresh.token();
            if (isUsableToken(refreshed)) {
                result = sendOnce(refreshed.getAccessToken(), autoReply, trigger, replyMessage);
            }
            if (!result.success() && MeituanAuthFailureUtil.isAuthFailure(result.errorCode(), result.responseSummary())) {
                authorizationFailureHandler.handleConfirmedAuthFailure(
                        autoReply.getShopId(), "IM_AUTO_REPLY_SEND", result.errorCode(), result.responseSummary());
                stateService.markStatus(autoReply.getId(), MtImAutoReplyStatusEnum.SKIPPED, "AUTH_REQUIRED");
                throw new MtAsyncTaskTerminalFailureException("AUTH_REQUIRED");
            }
        }
        if (!result.success()) {
            stateService.markSendFailure(autoReply.getId(), result.errorCode(), result.responseSummary());
            throw new IllegalStateException(StringUtils.defaultIfBlank(result.errorCode(), "IM_SEND_FAILED"));
        }
        stateService.markSent(autoReply.getId());
    }

    private MtImMessageClient.SendResult sendOnce(String accessToken,
                                                   MtImAutoReply autoReply,
                                                   MtImMessage trigger,
                                                   MtImMessage replyMessage) {
        return messageClient.sendTwoPartyMessage(accessToken, new MtImMessageClient.TwoPartyMessageCommand(
                autoReply.getOutboundMessageId(), replyMessage.getMsgContentPlaintext(),
                ImMessageTypeEnum.TEXT.getCode(), replyMessage.getMessageCts(),
                trigger.getOrderId() == null ? 0L : trigger.getOrderId(), trigger.getOpenUserId(),
                trigger.getSceneTag(), trigger.getSceneName(), trigger.getExtendedField()));
    }

    private SkipDecision checkShopReadiness(Long shopId) {
        if (!mtProperties.getImAutoReply().isEnabled()) {
            return SkipDecision.skipped("GLOBAL_SWITCH_DISABLED");
        }
        if (!settingsService.isAutoReplyEnabled(shopId)) {
            return SkipDecision.skipped("SHOP_SWITCH_DISABLED");
        }
        if (!shopRightService.hasShopRight(shopId, RequiresShopRight.Logical.OR,
                MtShopRightCodeConstant.VIP_CODE, MtShopRightCodeConstant.H5_PROMOTION)) {
            return SkipDecision.skipped("SHOP_RIGHT_MISSING");
        }
        MtAccessToken token = accessTokenService.findByShopId(shopId);
        if (token == null || Integer.valueOf(MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode()).equals(token.getAuthStatus())) {
            return SkipDecision.skipped("AUTH_REQUIRED");
        }
        return null;
    }

    /**
     * 商家静默窗口：商家人工发言后的一段时间内不抢答，把接待机会留给人工。
     *
     * <p>能走到这里说明商家最后一次发言早于本条触发消息（更晚的发言已被
     * {@link #checkNewerMessages} 终结为 SKIPPED），因此窗口截止时刻是一个固定的历史时间点，
     * 只会到期、不会被推迟，单次等待上限即为 merchantHoldSeconds，不存在无限等待。</p>
     *
     * <p>需求所说的「滚动重新计时」由每条新顾客消息各自计算窗口自然产生，无需额外状态。</p>
     */
    private void awaitMerchantHoldWindow(MtImMessage trigger) {
        long holdSeconds = mtProperties.getImAutoReply().getMerchantHoldSeconds();
        if (holdSeconds <= 0) {
            return;
        }
        MtImMessage lastMerchant =
                messageMapper.selectLatestMerchantMessageInConversation(trigger.getConversationId());
        if (lastMerchant == null || lastMerchant.getEventTime() == null) {
            return;
        }
        LocalDateTime releaseAt = lastMerchant.getEventTime().plusSeconds(holdSeconds);
        long waitSeconds = secondsUntil(releaseAt);
        if (waitSeconds > 0) {
            throw new MtAsyncTaskDryRunException("MERCHANT_HOLD_WINDOW", waitSeconds);
        }
    }

    /**
     * 生成后的随机发送延迟，避免秒回显得机械。
     *
     * <p>延迟到期时刻必须落库：任务因等待被重排后会重新执行，若每次重新随机则永远到不了发送时刻。</p>
     */
    private void awaitSendDelay(MtImAutoReply autoReply) {
        LocalDateTime sendNotBefore = autoReply.getSendNotBefore();
        if (sendNotBefore == null) {
            sendNotBefore = stateService.assignSendNotBefore(autoReply.getId(), randomSendDelaySeconds());
        }
        long waitSeconds = secondsUntil(sendNotBefore);
        if (waitSeconds > 0) {
            throw new MtAsyncTaskDryRunException("SEND_DELAY", waitSeconds);
        }
    }

    private long randomSendDelaySeconds() {
        long min = Math.max(mtProperties.getImAutoReply().getSendDelayMinSeconds(), 0L);
        long max = Math.max(mtProperties.getImAutoReply().getSendDelayMaxSeconds(), min);
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /**
     * 向上取整的剩余秒数；worker 轮询精度约 1 秒，取整可避免因不足 1 秒被判为已到期而提前发送。
     */
    private long secondsUntil(LocalDateTime target) {
        if (target == null) {
            return 0L;
        }
        long millis = Duration.between(LocalDateTime.now(), target).toMillis();
        return millis <= 0 ? 0L : (millis + 999L) / 1000L;
    }

    private SkipDecision checkNewerMessages(MtImMessage trigger) {
        List<MtImMessage> newer = messageMapper.selectAfterMessageInConversation(
                trigger.getConversationId(), trigger.getId());
        if (newer == null) {
            return null;
        }
        for (MtImMessage message : newer) {
            // 商家侧只排除卡片：卡片由平台推送，不是人工回复。图片等其它类型仍视为人工已介入，
            // 判错的代价是顾客同时收到人工回复和自动回复，因此这里不收窄成 isConversational
            if (ImMessageSenderRoleEnum.MERCHANT.name().equals(message.getSenderRole())
                    && !ImMessageTypeEnum.isCard(message.getMsgType())) {
                return SkipDecision.skipped("MANUAL_REPLY_AFTER_TRIGGER");
            }
            // 只有能自己触发回复的用户消息才可以顶掉本次回复；否则 CARD 之类顶掉后无人接手，
            // 用户的提问会静默地得不到任何答复
            if (ImMessageSenderRoleEnum.USER.name().equals(message.getSenderRole())
                    && ImMessageTypeEnum.isReplyTriggerable(message.getMsgType())) {
                return new SkipDecision(MtImAutoReplyStatusEnum.SUPERSEDED, "NEWER_USER_MESSAGE");
            }
        }
        return null;
    }

    private boolean isSensitive(String content) {
        SensitiveWordHitDTO hit = sensitiveWordService.detect(content);
        return hit != null && hit.isHit();
    }

    private boolean isUsableToken(MtAccessToken token) {
        return token != null
                && Integer.valueOf(MtAccessTokenAuthStatusEnum.VALID.getCode()).equals(token.getAuthStatus())
                && StringUtils.isNotBlank(token.getAccessToken())
                && token.getExpiresAt() != null
                && token.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private boolean isTerminal(String status) {
        return MtImAutoReplyStatusEnum.SENT.name().equals(status)
                || MtImAutoReplyStatusEnum.SUPERSEDED.name().equals(status)
                || MtImAutoReplyStatusEnum.SKIPPED.name().equals(status)
                || MtImAutoReplyStatusEnum.DEAD.name().equals(status);
    }

    private void validatePayload(MtImAutoReplyTaskPayload payload) {
        if (payload == null || payload.getAutoReplyId() == null || payload.getTriggerMessageId() == null
                || payload.getConversationId() == null || payload.getSessionId() == null || payload.getShopId() == null) {
            throw new MtAsyncTaskTerminalFailureException("IM_AUTO_REPLY_TASK_PAYLOAD_INVALID");
        }
    }

    private record SkipDecision(MtImAutoReplyStatusEnum status, String reason) {
        static SkipDecision skipped(String reason) {
            return new SkipDecision(MtImAutoReplyStatusEnum.SKIPPED, reason);
        }
    }
}
