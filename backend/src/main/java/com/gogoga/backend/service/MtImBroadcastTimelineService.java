package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImBroadcastRecord;
import com.gogoga.backend.entity.mt.MtImBroadcastTask;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageDecryptStatusEnum;
import com.gogoga.backend.enums.ImMessageDeliveryStatusEnum;
import com.gogoga.backend.enums.ImMessageParseStatusEnum;
import com.gogoga.backend.enums.ImMessageRecordSourceEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.enums.ImMessageTypeEnum;
import com.gogoga.backend.mapper.MtImMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 把已成功群发的消息补写进会话时间线。
 *
 * <p>顾客回复群发消息时，AI 的上下文里必须能看到商家刚发了什么，否则会答非所问。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtImBroadcastTimelineService {

    private final MtProperties mtProperties;
    private final MtImMessageMapper messageMapper;
    private final MtImConversationService conversationService;
    private final MtImMessageCryptoService cryptoService;

    /**
     * 独立事务写入：消息已真实投递给顾客，时间线补写失败不得回滚发送结果。
     *
     * <p>异常由调用方捕获并降级为日志——此处不自调用包一层 try/catch，否则会绕过事务代理。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void persistSentMessage(MtImBroadcastTask task, MtImBroadcastRecord record) {
        if (!mtProperties.getImBroadcast().isPersistTimelineMessage()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MtImConversationService.Assignment assignment =
                conversationService.assign(task.getShopId(), record.getOpenUserId(), now);

        MtImMessage message = new MtImMessage();
        message.setShopId(task.getShopId());
        message.setConversationId(assignment.conversation().getId());
        message.setSessionId(assignment.session().getId());
        message.setRecordSource(ImMessageRecordSourceEnum.BROADCAST.name());
        message.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());
        message.setDeliveryStatus(ImMessageDeliveryStatusEnum.SENT.name());
        message.setEventTime(now);
        message.setBizType(1);
        message.setPlatformMsgId(record.getOutboundMessageId());
        message.setMsgType(ImMessageTypeEnum.TEXT.getCode());
        message.setMsgSource(1);
        message.setMessageCts(now.atZone(ZoneId.systemDefault()).toEpochSecond());
        message.setOrderId(0L);
        message.setOpenUserId(record.getOpenUserId());
        message.setHasReply(2);
        message.setMsgContentPlaintext(task.getContent());
        message.setMsgContentHash(cryptoService.sha256(task.getContent()));
        message.setParseStatus(ImMessageParseStatusEnum.SUCCESS.name());
        message.setDecryptStatus(ImMessageDecryptStatusEnum.NOT_APPLICABLE.name());
        message.setReceivedAt(now);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        messageMapper.insert(message);
        conversationService.recordMessage(message);
    }
}
