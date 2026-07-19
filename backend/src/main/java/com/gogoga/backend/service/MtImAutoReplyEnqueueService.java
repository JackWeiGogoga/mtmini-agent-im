package com.gogoga.backend.service;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtImAutoReply;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageDecryptStatusEnum;
import com.gogoga.backend.enums.ImMessageParseStatusEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.enums.ImMessageTypeEnum;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.enums.MtImAutoReplyStatusEnum;
import com.gogoga.backend.mapper.MtImAutoReplyMapper;
import com.gogoga.backend.service.async.MtImAutoReplyTaskPayload;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
/**
 * 在 IM 入库事务内创建自动回复事实和数据库异步任务。
 */
@Service
@RequiredArgsConstructor
public class MtImAutoReplyEnqueueService {

    private final MtProperties mtProperties;
    private final ShopOperationSettingsService shopOperationSettingsService;
    private final MtShopRightService mtShopRightService;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtImAutoReplyMapper autoReplyMapper;
    private final MtAsyncTaskService asyncTaskService;
    private final ImOutboundMessageIdGenerator messageIdGenerator;

    public MtImAutoReply enqueueIfEligible(MtImMessage message) {
        if (!isEligible(message)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        MtImAutoReply autoReply = new MtImAutoReply();
        autoReply.setShopId(message.getShopId());
        autoReply.setConversationId(message.getConversationId());
        autoReply.setSessionId(message.getSessionId());
        autoReply.setTriggerMessageId(message.getId());
        autoReply.setOutboundMessageId(messageIdGenerator.nextId());
        autoReply.setStatus(MtImAutoReplyStatusEnum.PENDING.name());
        autoReply.setRagUsed(false);
        autoReply.setRagChunkCount(0);
        autoReply.setRewriteCount(0);
        autoReply.setCreateTime(now);
        autoReply.setUpdateTime(now);
        try {
            autoReplyMapper.insert(autoReply);
        } catch (DuplicateKeyException duplicate) {
            MtImAutoReply existing = autoReplyMapper.selectByTriggerMessageId(message.getId());
            if (existing != null) {
                return existing;
            }
            throw duplicate;
        }

        MtImAutoReplyTaskPayload payload = new MtImAutoReplyTaskPayload();
        payload.setShopId(message.getShopId());
        payload.setConversationId(message.getConversationId());
        payload.setSessionId(message.getSessionId());
        payload.setAutoReplyId(autoReply.getId());
        payload.setTriggerMessageId(message.getId());
        asyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.IM_AUTO_REPLY,
                String.valueOf(autoReply.getId()),
                String.valueOf(message.getConversationId()),
                payload,
                now.plusNanos(Math.max(mtProperties.getImAutoReply().getDebounceMillis(), 0L) * 1_000_000L),
                mtProperties.getAsyncTask().getImAutoReply().getMaxRetryCount()
        );
        return autoReply;
    }

    private boolean isEligible(MtImMessage message) {
        if (!mtProperties.getImAutoReply().isEnabled() || message == null || message.getId() == null) {
            return false;
        }
        if (!ImMessageParseStatusEnum.SUCCESS.name().equals(message.getParseStatus())
                || !ImMessageDecryptStatusEnum.SUCCESS.name().equals(message.getDecryptStatus())
                || !ImMessageSenderRoleEnum.USER.name().equals(message.getSenderRole())
                || !Integer.valueOf(1).equals(message.getBizType())
                || !Integer.valueOf(ImMessageTypeEnum.TEXT.getCode()).equals(message.getMsgType())
                || Integer.valueOf(2).equals(message.getHasReply())
                || StringUtils.isBlank(message.getMsgContentPlaintext())
                || message.getShopId() == null || message.getConversationId() == null
                || message.getSessionId() == null || message.getOpenUserId() == null) {
            return false;
        }
        if (!shopOperationSettingsService.isAutoReplyEnabled(message.getShopId())) {
            return false;
        }
        if (!mtShopRightService.hasShopRight(
                message.getShopId(),
                RequiresShopRight.Logical.OR,
                MtShopRightCodeConstant.VIP_CODE,
                MtShopRightCodeConstant.H5_PROMOTION)) {
            return false;
        }
        MtAccessToken token = mtAccessTokenService.findByShopId(message.getShopId());
        return token != null
                && Integer.valueOf(MtAccessTokenAuthStatusEnum.VALID.getCode()).equals(token.getAuthStatus())
                && StringUtils.isNotBlank(token.getAccessToken());
    }

}
