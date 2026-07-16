package com.gogoga.backend.service.impl;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.client.MtCommentImClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.dto.CommentImMessageGenerationResultDTO;
import com.gogoga.backend.dto.MtImCreateGroupResultDTO;
import com.gogoga.backend.dto.MtImSendMessageResultDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentImFollowUp;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentImFollowUpFailedStageEnum;
import com.gogoga.backend.enums.CommentImFollowUpStatusEnum;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.enums.MtCommentSensitiveTypeEnum;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentImFollowUpMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.CommentImMessageGenerationService;
import com.gogoga.backend.service.MtCommentImFollowUpService;
import com.gogoga.backend.service.MtImMessageCryptoService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.MtShopRightService;
import com.gogoga.backend.service.ShopOperationSettingsService;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtCommentAutoImFollowUpTaskPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 差评评价匿名群聊跟进状态机。美团建群和发送各最多真实调用一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtCommentImFollowUpServiceImpl implements MtCommentImFollowUpService {

    private static final String POWER_SOURCE_TYPE = "COMMENT_IM_FOLLOW_UP";

    private final MtProperties mtProperties;
    private final MtCommentMapper mtCommentMapper;
    private final MtCommentImFollowUpMapper imFollowUpMapper;
    private final MtShopMapper mtShopMapper;
    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final ShopOperationSettingsService shopOperationSettingsService;
    private final MtShopRightService mtShopRightService;
    private final CommentImMessageGenerationService messageGenerationService;
    private final MtPowerConsumeService mtPowerConsumeService;
    private final MtImMessageCryptoService cryptoService;
    private final MtCommentImClient imClient;

    @Override
    public void executeCommentAutoImFollowUpTask(MtCommentAutoImFollowUpTaskPayload payload) {
        long startNanos = System.nanoTime();
        validatePayload(payload);
        MtComment comment = mtCommentMapper.selectByCommentId(payload.getCommentId());
        if (comment == null) {
            log.warn("差评IM跟进跳过，评价不存在，shopId={}, commentId={}", payload.getShopId(), payload.getCommentId());
            return;
        }
        if (!payload.getShopId().equals(comment.getShopId())) {
            throw new IllegalStateException("COMMENT_IM_SHOP_MISMATCH");
        }
        MtShop shop = mtShopMapper.selectById(payload.getShopId());
        if (shop == null || StringUtils.isBlank(shop.getEpoiId())) {
            throw new IllegalStateException("COMMENT_IM_SHOP_INVALID");
        }

        MtCommentImFollowUp record = selectOrCreate(shop, comment);
        if (isTerminal(record)) {
            if (StringUtils.equals(record.getStatus(), CommentImFollowUpStatusEnum.FAILED.name())) {
                throw terminalFailure(record.getErrorCode(), record.getErrorMessage());
            }
            return;
        }
        if (!isExecutionContextValid(shop, comment, record, startNanos)) {
            return;
        }
        MtAccessToken token = mtAccessTokenMapper.selectByShopId(shop.getId());
        if (token == null || StringUtils.isBlank(token.getAccessToken())) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED,
                    CommentImFollowUpFailedStageEnum.CREATE_GROUP, "COMMENT_IM_TOKEN_MISSING",
                    "当前门店授权 token 不可用", startNanos);
            return;
        }

        if (StringUtils.equals(record.getStatus(), CommentImFollowUpStatusEnum.PENDING.name())) {
            if (!generateAndCharge(shop, comment, record, startNanos)) {
                return;
            }
            record = requireRecord(shop.getId(), comment.getCommentId());
        }

        if (StringUtils.equals(record.getStatus(), CommentImFollowUpStatusEnum.GENERATED.name())) {
            if (mtProperties.getComment().getImFollowUp().isCreateGroupDryRun()) {
                throw new MtAsyncTaskDryRunException("MT_COMMENT_IM_CREATE_GROUP_DRY_RUN",
                        mtProperties.getComment().getImFollowUp().getDryRunRescheduleSeconds());
            }
            if (!createGroupOnce(token, comment, record, startNanos)) {
                return;
            }
            record = requireRecord(shop.getId(), comment.getCommentId());
        }

        if (StringUtils.equals(record.getStatus(), CommentImFollowUpStatusEnum.GROUP_CREATED.name())) {
            sendMessageOnce(token, comment, record, startNanos);
        }
    }

    private boolean isExecutionContextValid(MtShop shop, MtComment comment, MtCommentImFollowUp record, long startNanos) {
        if (!Integer.valueOf(1).equals(comment.getValid())) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.SKIPPED, null,
                    "COMMENT_IM_INVALID", "评价已失效", startNanos);
            return false;
        }
        if (!MtCommentSensitiveTypeEnum.isProcessable(comment.getCommentType())) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.SKIPPED, null,
                    "COMMENT_IM_TYPE_UNSUPPORTED", "评论敏感类型不处理", startNanos);
            return false;
        }
        if (!shopOperationSettingsService.isAutoReplyEnabled(shop.getId())
                || !shopOperationSettingsService.isNegativeCommentAppealEnabled(shop.getId())) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.SKIPPED, null,
                    "COMMENT_IM_SWITCH_DISABLED", "差评处理开关已关闭", startNanos);
            return false;
        }
        if (!Integer.valueOf(1).equals(comment.getReplyStatus())
                || !StringUtils.equals(comment.getAutoReplyStatus(), "SUCCESS")) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.SKIPPED, null,
                    "COMMENT_IM_AUTO_REPLY_NOT_SUCCESS", "评价自动回复尚未成功", startNanos);
            return false;
        }
        if (CommentReplySentimentEnum.fromScores(
                comment.getProductScore(),
                comment.getPackingScore(),
                comment.getDeliveryCommentScore(),
                comment.getFoodCommentScore()) != CommentReplySentimentEnum.BAD) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.SKIPPED, null,
                    "COMMENT_IM_NOT_BAD", "当前评价不是差评", startNanos);
            return false;
        }
        boolean hasRight = mtShopRightService.hasShopRight(shop.getId(), RequiresShopRight.Logical.OR,
                MtShopRightCodeConstant.VIP_CODE, MtShopRightCodeConstant.H5_PROMOTION);
        if (!hasRight) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.SKIPPED, null,
                    "COMMENT_IM_RIGHT_INVALID", "门店评价处理权益不可用", startNanos);
            return false;
        }
        return true;
    }

    private boolean generateAndCharge(MtShop shop, MtComment comment, MtCommentImFollowUp record, long startNanos) {
        try {
            CommentImMessageGenerationResultDTO generated = messageGenerationService.generate(comment);
            consumePower(shop.getId(), comment.getCommentId(), generated.getPowerAmount());
            LocalDateTime now = LocalDateTime.now();
            record.setMessageContent(generated.getMessageContent());
            record.setMessageSource(generated.getMessageSource());
            record.setFallbackReason(generated.getFallbackReason());
            record.setModelProvider(generated.getModelProvider());
            record.setModel(generated.getModel());
            record.setPromptKey(generated.getPromptKey());
            record.setPromptVersion(generated.getPromptVersion());
            record.setPromptTokens(generated.getPromptTokens());
            record.setCompletionTokens(generated.getCompletionTokens());
            record.setTotalTokens(generated.getTotalTokens());
            record.setPowerAmount(generated.getPowerAmount());
            record.setGeneratedAt(now);
            record.setGenerationDurationMs(generated.getDurationMs());
            record.setDurationMs(durationMs(startNanos));
            record.setUpdateTime(now);
            imFollowUpMapper.markGenerated(record);
            mtCommentMapper.markImFollowUpStatus(comment.getId(), CommentImFollowUpStatusEnum.GENERATED.name(),
                    null, null, null, now);
            return true;
        } catch (Exception e) {
            CommentImFollowUpFailedStageEnum stage = isPowerError(e)
                    ? CommentImFollowUpFailedStageEnum.POWER
                    : CommentImFollowUpFailedStageEnum.GENERATE;
            markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED, stage,
                    stage == CommentImFollowUpFailedStageEnum.POWER ? "COMMENT_IM_POWER_FAILED" : "COMMENT_IM_GENERATE_FAILED",
                    safeError(e), startNanos);
            return false;
        }
    }

    private boolean createGroupOnce(MtAccessToken token, MtComment comment, MtCommentImFollowUp record, long startNanos) {
        if (record.getGroupId() != null) {
            return true;
        }
        if (record.getCreateGroupAttemptedAt() != null) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED,
                    CommentImFollowUpFailedStageEnum.CREATE_GROUP, "COMMENT_IM_CREATE_ALREADY_ATTEMPTED",
                    "建群真实调用已尝试，不再重试", startNanos);
            return false;
        }
        LocalDateTime attemptedAt = LocalDateTime.now();
        if (imFollowUpMapper.claimCreateGroupAttempt(record.getId(), attemptedAt, attemptedAt) != 1) {
            return false;
        }
        long stageNanos = System.nanoTime();
        try {
            MtImCreateGroupResultDTO result = imClient.createCommentGroup(token.getAccessToken(), comment.getCommentId());
            LocalDateTime now = LocalDateTime.now();
            record.setGroupId(result.getGroupId());
            record.setNewGroup(result.getNewGroup());
            record.setGroupCtime(result.getCtime());
            record.setGroupState(result.getState());
            record.setGroupCloseType(result.getCloseType());
            record.setCreateGroupResponseSummary(result.getResponseSummary());
            record.setGroupCreatedAt(now);
            record.setCreateGroupDurationMs(durationMs(stageNanos));
            record.setDurationMs(durationMs(startNanos));
            record.setUpdateTime(now);
            imFollowUpMapper.markGroupCreated(record);
            mtCommentMapper.markImFollowUpStatus(comment.getId(), CommentImFollowUpStatusEnum.GROUP_CREATED.name(),
                    result.getGroupId(), null, null, now);
            log.info("差评IM建群成功，shopId={}, commentId={}, groupId={}, durationMs={}",
                    comment.getShopId(), comment.getCommentId(), result.getGroupId(), durationMs(stageNanos));
            return true;
        } catch (Exception e) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED,
                    CommentImFollowUpFailedStageEnum.CREATE_GROUP, "COMMENT_IM_CREATE_GROUP_FAILED",
                    safeError(e), startNanos);
            return false;
        }
    }

    private void sendMessageOnce(MtAccessToken token, MtComment comment, MtCommentImFollowUp record, long startNanos) {
        if (mtProperties.getComment().getImFollowUp().isSendMessageDryRun()) {
            throw new MtAsyncTaskDryRunException("MT_COMMENT_IM_SEND_MESSAGE_DRY_RUN",
                    mtProperties.getComment().getImFollowUp().getDryRunRescheduleSeconds());
        }

        final String encryptedContent;
        try {
            encryptedContent = cryptoService.encrypt(record.getMessageContent());
        } catch (Exception e) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED,
                    CommentImFollowUpFailedStageEnum.ENCRYPT, "COMMENT_IM_ENCRYPT_FAILED", safeError(e), startNanos);
            return;
        }

        if (record.getMessageId() == null) {
            try {
                initializeMessageIdentity(record);
                record = requireRecord(comment.getShopId(), comment.getCommentId());
            } catch (Exception e) {
                markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED,
                        CommentImFollowUpFailedStageEnum.SEND_MESSAGE, "COMMENT_IM_MESSAGE_ID_FAILED",
                        safeError(e), startNanos);
                return;
            }
        }
        if (record.getSendAttemptedAt() != null) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED,
                    CommentImFollowUpFailedStageEnum.SEND_MESSAGE, "COMMENT_IM_SEND_ALREADY_ATTEMPTED",
                    "发消息真实调用已尝试，不再重试", startNanos);
            return;
        }
        LocalDateTime attemptedAt = LocalDateTime.now();
        if (imFollowUpMapper.claimSendAttempt(record.getId(), attemptedAt, attemptedAt) != 1) {
            return;
        }
        long stageNanos = System.nanoTime();
        try {
            MtImSendMessageResultDTO result = imClient.sendCommentMessage(
                    token.getAccessToken(), comment.getCommentId(), record.getGroupId(),
                    record.getMessageId(), record.getMessageCts(), encryptedContent);
            LocalDateTime now = LocalDateTime.now();
            record.setSendResponseSummary(result.getResponseSummary());
            record.setSentAt(now);
            record.setSendDurationMs(durationMs(stageNanos));
            record.setDurationMs(durationMs(startNanos));
            record.setUpdateTime(now);
            imFollowUpMapper.markSuccess(record);
            mtCommentMapper.markImFollowUpStatus(comment.getId(), CommentImFollowUpStatusEnum.SUCCESS.name(),
                    record.getGroupId(), now, null, now);
            log.info("差评IM消息发送成功，shopId={}, commentId={}, groupId={}, messageId={}, durationMs={}",
                    comment.getShopId(), comment.getCommentId(), record.getGroupId(), record.getMessageId(), durationMs(stageNanos));
        } catch (Exception e) {
            markTerminal(comment, record, CommentImFollowUpStatusEnum.FAILED,
                    CommentImFollowUpFailedStageEnum.SEND_MESSAGE, "COMMENT_IM_SEND_FAILED", safeError(e), startNanos);
        }
    }

    private void initializeMessageIdentity(MtCommentImFollowUp record) {
        for (int i = 0; i < 3; i++) {
            long messageId = System.currentTimeMillis() * 1_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L);
            long cts = Instant.now().getEpochSecond();
            try {
                if (imFollowUpMapper.initializeMessageIdentity(record.getId(), messageId, cts,
                        cryptoService.sha256(record.getMessageContent()), LocalDateTime.now()) == 1) {
                    return;
                }
                return;
            } catch (DuplicateKeyException ignored) {
                // 仅重试本地消息ID生成，不触发任何外部调用。
            }
        }
        throw new IllegalStateException("COMMENT_IM_MESSAGE_ID_CONFLICT");
    }

    private MtCommentImFollowUp selectOrCreate(MtShop shop, MtComment comment) {
        MtCommentImFollowUp existing = imFollowUpMapper.selectByShopIdAndCommentId(shop.getId(), comment.getCommentId());
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        MtCommentImFollowUp record = new MtCommentImFollowUp();
        record.setShopId(shop.getId());
        record.setMtCommentId(comment.getId());
        record.setCommentId(comment.getCommentId());
        record.setStatus(CommentImFollowUpStatusEnum.PENDING.name());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            imFollowUpMapper.insert(record);
            return record;
        } catch (DuplicateKeyException e) {
            return requireRecord(shop.getId(), comment.getCommentId());
        }
    }

    private MtCommentImFollowUp requireRecord(Long shopId, Long commentId) {
        MtCommentImFollowUp record = imFollowUpMapper.selectByShopIdAndCommentId(shopId, commentId);
        if (record == null) {
            throw new IllegalStateException("COMMENT_IM_RECORD_MISSING");
        }
        return record;
    }

    private void consumePower(Long shopId, Long commentId, Integer powerAmount) {
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(POWER_SOURCE_TYPE);
        request.setSourceId(String.valueOf(commentId));
        request.setRequestNo(POWER_SOURCE_TYPE + ":" + commentId);
        request.setPowerAmount(Math.max(powerAmount == null ? 0 : powerAmount, 1));
        request.setRemark("差评匿名群聊消息生成");
        mtPowerConsumeService.consume(request);
    }

    private void markTerminal(MtComment comment, MtCommentImFollowUp record,
                              CommentImFollowUpStatusEnum status, CommentImFollowUpFailedStageEnum stage,
                              String errorCode, String errorMessage, long startNanos) {
        String safeMessage = StringUtils.left(StringUtils.defaultIfBlank(errorMessage, errorCode), 1000);
        LocalDateTime now = LocalDateTime.now();
        imFollowUpMapper.markTerminal(record.getId(), status.name(), stage == null ? null : stage.name(),
                errorCode, safeMessage, durationMs(startNanos), now);
        mtCommentMapper.markImFollowUpStatus(comment.getId(), status.name(), record.getGroupId(),
                null, safeMessage, now);
        log.warn("差评IM跟进终止，shopId={}, commentId={}, status={}, stage={}, errorCode={}",
                comment.getShopId(), comment.getCommentId(), status.name(), stage, errorCode);
        if (status == CommentImFollowUpStatusEnum.FAILED) {
            throw terminalFailure(errorCode, safeMessage);
        }
    }

    private MtAsyncTaskTerminalFailureException terminalFailure(String errorCode, String errorMessage) {
        String summary = StringUtils.defaultIfBlank(errorCode, "COMMENT_IM_FAILED");
        if (StringUtils.isNotBlank(errorMessage) && !StringUtils.equals(errorMessage, summary)) {
            summary += ":" + errorMessage;
        }
        return new MtAsyncTaskTerminalFailureException(StringUtils.left(summary, 1000));
    }

    private boolean isTerminal(MtCommentImFollowUp record) {
        try {
            return CommentImFollowUpStatusEnum.valueOf(record.getStatus()).isTerminal();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPowerError(Exception e) {
        String message = e == null ? null : e.getMessage();
        return StringUtils.contains(message, "算力") || StringUtils.contains(message, "POWER");
    }

    private String safeError(Exception e) {
        return StringUtils.left(StringUtils.defaultIfBlank(e == null ? null : e.getMessage(), "COMMENT_IM_FAILED"), 1000);
    }

    private int durationMs(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(millis, 0L);
    }

    private void validatePayload(MtCommentAutoImFollowUpTaskPayload payload) {
        if (payload == null || payload.getShopId() == null || payload.getCommentId() == null) {
            throw new IllegalArgumentException("COMMENT_AUTO_IM_FOLLOW_UP_PAYLOAD_INVALID");
        }
    }
}
