package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageDecryptStatusEnum;
import com.gogoga.backend.enums.ImMessageDeliveryStatusEnum;
import com.gogoga.backend.enums.ImMessageParseStatusEnum;
import com.gogoga.backend.enums.ImMessageRecordSourceEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.enums.ImMessageTypeEnum;
import com.gogoga.backend.enums.MtImAutoReplyStatusEnum;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import com.gogoga.backend.mapper.MtImMessageMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 自动回复各阶段的事务状态落库。
 */
@Service
@RequiredArgsConstructor
public class MtImAutoReplyStateService {

    private static final String POWER_SOURCE_TYPE = "IM_AUTO_REPLY";

    private final MtProperties mtProperties;
    private final PowerProperties powerProperties;
    private final MtImAutoReplyMapper autoReplyMapper;
    private final MtImMessageMapper messageMapper;
    private final MtImMessageCryptoService cryptoService;
    private final MtImConversationService conversationService;
    private final MtPowerConsumeService powerConsumeService;

    @Transactional(rollbackFor = Exception.class)
    public MtImMessage saveGenerated(Long autoReplyId,
                                     MtImMessage trigger,
                                     String replyContent,
                                     ImAutoReplyChatResult chatResult,
                                     ImAutoReplyRagService.RagContext ragContext,
                                     int rewriteCount,
                                     String userMessageCategory) {
        MtImAutoReply autoReply = requireAutoReply(autoReplyId);
        if (autoReply.getReplyMessageId() != null) {
            return messageMapper.selectById(autoReply.getReplyMessageId());
        }
        LocalDateTime now = LocalDateTime.now();
        MtImMessage message = new MtImMessage();
        message.setShopId(autoReply.getShopId());
        message.setConversationId(autoReply.getConversationId());
        message.setSessionId(autoReply.getSessionId());
        message.setRecordSource(ImMessageRecordSourceEnum.AI_AUTO_REPLY.name());
        message.setSenderRole(ImMessageSenderRoleEnum.AI.name());
        message.setReplyToMessageId(autoReply.getTriggerMessageId());
        message.setAutoReplyId(autoReply.getId());
        message.setDeliveryStatus(ImMessageDeliveryStatusEnum.GENERATED.name());
        // AI 生成的消息不来自美团回调，没有签名可验；sign_verified 为 NOT NULL，不能留空
        message.setSignVerified(false);
        message.setEventTime(now);
        message.setBizType(1);
        message.setPlatformMsgId(autoReply.getOutboundMessageId());
        message.setAppId(trigger.getAppId());
        message.setAppPoiCode(trigger.getAppPoiCode());
        message.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        message.setMsgSource(1);
        message.setMessageCts(now.atZone(ZoneId.systemDefault()).toEpochSecond());
        message.setOrderId(trigger.getOrderId() == null ? 0L : trigger.getOrderId());
        message.setOpenUserId(trigger.getOpenUserId());
        message.setSceneTag(trigger.getSceneTag());
        message.setSceneName(trigger.getSceneName());
        message.setHasReply(2);
        message.setMsgContentPlaintext(replyContent);
        message.setMsgContentHash(cryptoService.sha256(replyContent));
        message.setParseStatus(ImMessageParseStatusEnum.SUCCESS.name());
        message.setDecryptStatus(ImMessageDecryptStatusEnum.NOT_APPLICABLE.name());
        message.setReceivedAt(now);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        messageMapper.insert(message);
        conversationService.recordMessage(message);

        autoReply.setReplyMessageId(message.getId());
        autoReply.setStatus(MtImAutoReplyStatusEnum.GENERATED.name());
        autoReply.setUserMessageCategory(userMessageCategory);
        autoReply.setRagUsed(ragContext != null && ragContext.used());
        autoReply.setRagChunkCount(ragContext == null || ragContext.hits() == null ? 0 : ragContext.hits().size());
        autoReply.setRagMaxScore(ragContext == null || ragContext.maxCosineScore() == null
                ? null : BigDecimal.valueOf(ragContext.maxCosineScore()));
        autoReply.setModelProvider(mtProperties.getImAutoReply().getModelProvider());
        autoReply.setModelName(mtProperties.getImAutoReply().getChatModel());
        autoReply.setPromptTokens(chatResult.promptTokens());
        autoReply.setCompletionTokens(chatResult.completionTokens());
        autoReply.setRewriteCount(rewriteCount);
        autoReply.setGeneratedAt(now);
        autoReply.setLastErrorCode(null);
        autoReply.setLastErrorSummary(null);
        autoReply.setUpdateTime(now);
        autoReplyMapper.update(autoReply);
        return message;
    }

    @Transactional(rollbackFor = Exception.class)
    public MtImAutoReply bill(Long autoReplyId) {
        MtImAutoReply autoReply = requireAutoReply(autoReplyId);
        if (autoReply.getBilledAt() != null) {
            return autoReply;
        }
        int powerAmount = powerProperties.getBilling().calculateLlmPowerAmount(
                toLong(autoReply.getPromptTokens()), toLong(autoReply.getCompletionTokens()));
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(autoReply.getShopId());
        request.setSourceType(POWER_SOURCE_TYPE);
        request.setSourceId(String.valueOf(autoReply.getId()));
        request.setRequestNo(POWER_SOURCE_TYPE + ":" + autoReply.getId());
        request.setPowerAmount(powerAmount);
        request.setRemark("IM自动回复模型实际token计费");
        powerConsumeService.consume(request);

        LocalDateTime now = LocalDateTime.now();
        autoReply.setPowerAmount(powerAmount);
        autoReply.setBilledAt(now);
        autoReply.setStatus(MtImAutoReplyStatusEnum.BILLED.name());
        autoReply.setUpdateTime(now);
        autoReplyMapper.update(autoReply);
        return autoReply;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markStatus(Long autoReplyId, MtImAutoReplyStatusEnum status, String reason) {
        MtImAutoReply autoReply = requireAutoReply(autoReplyId);
        LocalDateTime now = LocalDateTime.now();
        autoReply.setStatus(status.name());
        autoReply.setSkipReason(StringUtils.left(reason, 64));
        autoReply.setUpdateTime(now);
        autoReplyMapper.update(autoReply);
        if (autoReply.getReplyMessageId() != null
                && (status == MtImAutoReplyStatusEnum.SKIPPED || status == MtImAutoReplyStatusEnum.SUPERSEDED)) {
            messageMapper.updateDeliveryStatus(
                    autoReply.getReplyMessageId(), ImMessageDeliveryStatusEnum.FAILED.name(),
                    reason, reason, now);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSending(Long autoReplyId) {
        MtImAutoReply autoReply = requireAutoReply(autoReplyId);
        LocalDateTime now = LocalDateTime.now();
        autoReply.setStatus(MtImAutoReplyStatusEnum.SENDING.name());
        autoReply.setLastErrorCode(null);
        autoReply.setLastErrorSummary(null);
        autoReply.setUpdateTime(now);
        autoReplyMapper.update(autoReply);
        messageMapper.updateDeliveryStatus(
                autoReply.getReplyMessageId(), ImMessageDeliveryStatusEnum.SENDING.name(), null, null, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSendFailure(Long autoReplyId, String errorCode, String errorSummary) {
        MtImAutoReply autoReply = requireAutoReply(autoReplyId);
        LocalDateTime now = LocalDateTime.now();
        autoReply.setStatus(MtImAutoReplyStatusEnum.BILLED.name());
        autoReply.setLastErrorCode(StringUtils.left(errorCode, 128));
        autoReply.setLastErrorSummary(StringUtils.left(errorSummary, 1000));
        autoReply.setUpdateTime(now);
        autoReplyMapper.update(autoReply);
        messageMapper.updateDeliveryStatus(
                autoReply.getReplyMessageId(), ImMessageDeliveryStatusEnum.FAILED.name(),
                StringUtils.left(errorCode, 128), StringUtils.left(errorSummary, 1000), now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSent(Long autoReplyId) {
        MtImAutoReply autoReply = requireAutoReply(autoReplyId);
        LocalDateTime now = LocalDateTime.now();
        autoReply.setStatus(MtImAutoReplyStatusEnum.SENT.name());
        autoReply.setSentAt(now);
        autoReply.setLastErrorCode(null);
        autoReply.setLastErrorSummary(null);
        autoReply.setUpdateTime(now);
        autoReplyMapper.update(autoReply);
        messageMapper.updateDeliveryStatus(
                autoReply.getReplyMessageId(), ImMessageDeliveryStatusEnum.SENT.name(), null, null, now);
    }

    private MtImAutoReply requireAutoReply(Long id) {
        MtImAutoReply autoReply = autoReplyMapper.selectById(id);
        if (autoReply == null) {
            throw new IllegalArgumentException("IM_AUTO_REPLY_NOT_FOUND");
        }
        return autoReply;
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
