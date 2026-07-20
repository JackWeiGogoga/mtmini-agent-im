package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.ImMessageDecryptStatusEnum;
import com.gogoga.backend.enums.ImMessageDeliveryStatusEnum;
import com.gogoga.backend.enums.ImMessageParseStatusEnum;
import com.gogoga.backend.enums.ImMessageRecordSourceEnum;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.mapper.MtImMessageMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.internal.utils.SignerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 美团 IM 两方会话消息验签、解析与可靠入库服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtImMessageIngestionService {

    private final MtProperties mtProperties;
    private final MtImMessageMapper mtImMessageMapper;
    private final MtShopMapper mtShopMapper;
    private final MtImMessageCryptoService cryptoService;
    private final MtImConversationService conversationService;
    private final MtImAutoReplyEnqueueService autoReplyEnqueueService;

    @Transactional(rollbackFor = Exception.class)
    public IngestionResult ingest(Map<String, String> parameters) {
        VerificationResult verification = verify(parameters);
        if (!verification.valid()) {
            log.warn("美团IM回调验签失败，callbackMsgId={}, errorCode={}, {}",
                    safeId(parameters == null ? null : parameters.get("msgId")),
                    verification.errorCode(),
                    verificationDiagnostics(parameters));
            return IngestionResult.failure(verification.errorCode());
        }

        String callbackMsgId = StringUtils.trimToNull(parameters.get("msgId"));
        if (callbackMsgId == null) {
            return IngestionResult.failure("CALLBACK_MSG_ID_MISSING");
        }
        MtImMessage existing = mtImMessageMapper.selectByCallbackMsgId(callbackMsgId);
        if (existing != null) {
            return IngestionResult.duplicate(existing.getId());
        }

        MtImMessage message = buildBaseMessage(parameters, callbackMsgId);
        parseMessage(message, parameters.get("message"));
        enrichTimelineMetadata(message);
        if (message.getAppId() != null && message.getPlatformMsgId() != null) {
            MtImMessage platformExisting = mtImMessageMapper.selectByPlatformMessage(
                    message.getAppId(), message.getPlatformMsgId());
            if (platformExisting != null) {
                if (ImMessageRecordSourceEnum.AI_AUTO_REPLY.name().equals(platformExisting.getRecordSource())) {
                    message.setId(platformExisting.getId());
                    mtImMessageMapper.mergeCallbackIntoInternalMessage(message);
                }
                return IngestionResult.duplicate(platformExisting.getId());
            }
        }

        assignConversation(message);

        try {
            mtImMessageMapper.insert(message);
        } catch (DuplicateKeyException duplicate) {
            MtImMessage duplicateMessage = findDuplicate(message);
            if (duplicateMessage != null) {
                return IngestionResult.duplicate(duplicateMessage.getId());
            }
            throw duplicate;
        }
        conversationService.recordMessage(message);
        autoReplyEnqueueService.enqueueIfEligible(message);
        log.info("美团IM消息已入库，recordId={}, shopId={}, callbackMsgId={}, platformMsgId={}, msgType={}, parseStatus={}, decryptStatus={}",
                message.getId(), message.getShopId(), safeId(message.getCallbackMsgId()),
                message.getPlatformMsgId(), message.getMsgType(), message.getParseStatus(), message.getDecryptStatus());
        return IngestionResult.success(message.getId());
    }

    /**
     * 验签失败时的排障信息。
     *
     * <p>默认只输出控制字段的期望值与实际值、参与签名的参数名集合，以及 sign 和 message 的
     * 指纹；不输出 signKey、message 密文和完整 sign。需要逐字节复核签名算法时，可临时把
     * {@code mt.im.callback-debug-log} 打开，输出完整原始参数——该模式会把消息密文写入日志，
     * 只应在测试门店联调期间短暂启用。</p>
     */
    private String verificationDiagnostics(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "parameters=EMPTY";
        }
        MtProperties.Im im = mtProperties.getIm();
        if (im != null && im.isCallbackDebugLog()) {
            return "rawParameters=" + JsonUtil.toJson(new java.util.TreeMap<>(parameters));
        }
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("expectedDeveloperId", mtProperties.getDeveloperId());
        diagnostics.put("actualDeveloperId", parameters.get("developerId"));
        diagnostics.put("expectedBusinessId", callbackBusinessId());
        diagnostics.put("actualBusinessId", parameters.get("businessId"));
        diagnostics.put("ePoiId", parameters.get("ePoiId"));
        diagnostics.put("opBizCode", parameters.get("opBizCode"));
        diagnostics.put("msgType", parameters.get("msgType"));
        diagnostics.put("timestamp", parameters.get("timestamp"));
        diagnostics.put("signKeyConfigured", StringUtils.isNotBlank(mtProperties.getSignKey()));
        diagnostics.put("parameterNames", new java.util.TreeSet<>(parameters.keySet()));
        diagnostics.put("signFingerprint", fingerprint(parameters.get("sign")));
        diagnostics.put("messageLength", StringUtils.length(parameters.get("message")));
        diagnostics.put("messageFingerprint", fingerprint(parameters.get("message")));
        return "diagnostics=" + JsonUtil.toJson(diagnostics);
    }

    /**
     * 取 SHA-256 前 8 个十六进制字符，够用于比对两次回调是否同值，不足以还原原文。
     */
    private String fingerprint(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return StringUtils.substring(cryptoService.sha256(value), 0, 8);
    }

    /**
     * IM 回调的 businessId 与 {@code mt.business-id}（OAuth / token 刷新 / H5 鉴权）不同源，
     * 必须走 {@code mt.im.callback-business-id}。
     */
    private Integer callbackBusinessId() {
        MtProperties.Im im = mtProperties.getIm();
        return im == null ? null : im.getCallbackBusinessId();
    }

    private VerificationResult verify(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return VerificationResult.failure("CALLBACK_PARAMETERS_EMPTY");
        }
        String suppliedSign = StringUtils.trimToNull(parameters.get("sign"));
        if (suppliedSign == null || StringUtils.isBlank(mtProperties.getSignKey())) {
            return VerificationResult.failure("CALLBACK_SIGN_MISSING");
        }
        if (mtProperties.getDeveloperId() == null
                || !StringUtils.equals(String.valueOf(mtProperties.getDeveloperId()), parameters.get("developerId"))) {
            return VerificationResult.failure("CALLBACK_DEVELOPER_MISMATCH");
        }
        Integer expectedBusinessId = callbackBusinessId();
        if (expectedBusinessId == null
                || !StringUtils.equals(String.valueOf(expectedBusinessId), parameters.get("businessId"))) {
            return VerificationResult.failure("CALLBACK_BUSINESS_MISMATCH");
        }
        Map<String, String> signingParameters = new LinkedHashMap<>();
        parameters.forEach((key, value) -> {
            if (!StringUtils.equals(key, "sign") && StringUtils.isNotBlank(value)) {
                signingParameters.put(key, value);
            }
        });
        try {
            String expectedSign = SignerUtil.getSign(mtProperties.getSignKey(), signingParameters);
            boolean matched = MessageDigest.isEqual(
                    expectedSign.getBytes(StandardCharsets.UTF_8),
                    suppliedSign.getBytes(StandardCharsets.UTF_8)
            );
            return matched ? VerificationResult.success() : VerificationResult.failure("CALLBACK_SIGN_INVALID");
        } catch (Exception ignored) {
            return VerificationResult.failure("CALLBACK_SIGN_ERROR");
        }
    }

    private MtImMessage buildBaseMessage(Map<String, String> parameters, String callbackMsgId) {
        LocalDateTime now = LocalDateTime.now();
        MtImMessage message = new MtImMessage();
        message.setCallbackMsgId(callbackMsgId);
        message.setCallbackMsgType(parameters.get("msgType"));
        message.setDeveloperId(parseLong(parameters.get("developerId")));
        message.setBusinessId(parseInteger(parameters.get("businessId")));
        message.setOpBizCode(parameters.get("opBizCode"));
        message.setEpoiId(parameters.get("ePoiId"));
        message.setCallbackTimestamp(parseLong(parameters.get("timestamp")));
        message.setSignVerified(true);
        message.setRecordSource(ImMessageRecordSourceEnum.MEITUAN_CALLBACK.name());
        message.setDeliveryStatus(ImMessageDeliveryStatusEnum.RECEIVED.name());
        message.setEventTime(now);
        message.setParseStatus(ImMessageParseStatusEnum.PARSE_FAILED.name());
        message.setDecryptStatus(ImMessageDecryptStatusEnum.NOT_APPLICABLE.name());
        message.setReceivedAt(now);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        return message;
    }

    private void enrichTimelineMetadata(MtImMessage message) {
        if (Integer.valueOf(2).equals(message.getMsgSource())) {
            message.setSenderRole(ImMessageSenderRoleEnum.USER.name());
        } else if (Integer.valueOf(1).equals(message.getMsgSource())) {
            message.setSenderRole(ImMessageSenderRoleEnum.MERCHANT.name());
        }
        message.setEventTime(resolveEventTime(message.getMessageCts(), message.getReceivedAt()));
    }

    private void assignConversation(MtImMessage message) {
        if (message.getShopId() == null || message.getOpenUserId() == null || message.getSenderRole() == null) {
            return;
        }
        MtImConversationService.Assignment assignment = conversationService.assign(
                message.getShopId(), message.getOpenUserId(), message.getEventTime());
        if (assignment != null) {
            message.setConversationId(assignment.conversation().getId());
            message.setSessionId(assignment.session().getId());
        }
    }

    private LocalDateTime resolveEventTime(Long cts, LocalDateTime fallback) {
        if (cts == null || cts <= 0L) {
            return fallback;
        }
        try {
            Instant instant = cts >= 1_000_000_000_000L
                    ? Instant.ofEpochMilli(cts)
                    : Instant.ofEpochSecond(cts);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (DateTimeException ignored) {
            return fallback;
        }
    }

    private void parseMessage(MtImMessage message, String rawMessage) {
        message.setRawMessageJson(rawMessage);
        if (StringUtils.isBlank(rawMessage)) {
            markParseFailure(message, "MESSAGE_EMPTY");
            return;
        }
        try {
            JsonNode envelope = JsonUtil.toBean(rawMessage, JsonNode.class);
            if (envelope == null || !envelope.isObject()) {
                markParseFailure(message, "MESSAGE_INVALID");
                return;
            }
            message.setBizType(integerValue(envelope, "biz_type"));
            message.setExtendedField(jsonOrText(envelope.get("extended_field")));
            message.setInnerSig(textValue(envelope, "sig"));
            message.setInnerTimestamp(longValue(envelope, "timestamp"));

            JsonNode pushContent = envelope.get("push_content");
            if (pushContent != null && pushContent.isTextual()) {
                pushContent = JsonUtil.toBean(pushContent.asText(), JsonNode.class);
            }
            if (pushContent == null || !pushContent.isObject()) {
                markParseFailure(message, "PUSH_CONTENT_INVALID");
                return;
            }
            message.setRawPushContentJson(pushContent.toString());
            mapPushContent(message, envelope, pushContent);
            mapShop(message);
            decryptContent(message);
            if (message.getShopId() == null) {
                message.setParseStatus(ImMessageParseStatusEnum.SHOP_UNMATCHED.name());
                message.setErrorCode("SHOP_UNMATCHED");
                message.setErrorSummary("SHOP_UNMATCHED");
            } else {
                message.setParseStatus(ImMessageParseStatusEnum.SUCCESS.name());
            }
        } catch (RuntimeException ignored) {
            markParseFailure(message, "MESSAGE_PARSE_FAILED");
        }
    }

    private void mapPushContent(MtImMessage target, JsonNode envelope, JsonNode pushContent) {
        target.setPlatformMsgId(firstLong(pushContent, envelope, "msg_id"));
        target.setAppId(firstLong(pushContent, envelope, "app_id"));
        target.setAppPoiCode(firstText(pushContent, envelope, "app_poi_code"));
        target.setMsgType(firstInteger(pushContent, envelope, "msg_type"));
        target.setMsgSource(firstInteger(pushContent, envelope, "msg_source"));
        target.setMessageCts(firstLong(pushContent, envelope, "cts"));
        target.setOrderId(firstLong(pushContent, envelope, "order_id"));
        target.setOpenUserId(firstLong(pushContent, envelope, "open_user_id"));
        target.setSceneTag(firstInteger(pushContent, envelope, "scene_tag"));
        target.setSceneName(firstText(pushContent, envelope, "scene_name"));
        target.setHasReply(firstInteger(pushContent, envelope, "has_reply"));
        target.setMsgContentCiphertext(firstText(pushContent, envelope, "msg_content"));
    }

    private void mapShop(MtImMessage message) {
        MtShop shop = null;
        if (StringUtils.isNotBlank(message.getAppPoiCode())) {
            shop = mtShopMapper.selectByEpoiId(message.getAppPoiCode());
        }
        if (shop == null && StringUtils.isNotBlank(message.getEpoiId())) {
            shop = mtShopMapper.selectByEpoiId(message.getEpoiId());
        }
        if (shop != null) {
            message.setShopId(shop.getId());
        }
    }

    private void decryptContent(MtImMessage message) {
        if (StringUtils.isBlank(message.getMsgContentCiphertext())) {
            message.setDecryptStatus(ImMessageDecryptStatusEnum.NOT_APPLICABLE.name());
            return;
        }
        try {
            String plaintext = cryptoService.decrypt(message.getMsgContentCiphertext());
            message.setMsgContentPlaintext(plaintext);
            message.setMsgContentHash(cryptoService.sha256(plaintext));
            message.setDecryptStatus(ImMessageDecryptStatusEnum.SUCCESS.name());
        } catch (RuntimeException ignored) {
            message.setDecryptStatus(ImMessageDecryptStatusEnum.FAILED.name());
            if (message.getErrorCode() == null) {
                message.setErrorCode("MESSAGE_DECRYPT_FAILED");
                message.setErrorSummary("MESSAGE_DECRYPT_FAILED");
            }
        }
    }

    private void markParseFailure(MtImMessage message, String errorCode) {
        message.setParseStatus(ImMessageParseStatusEnum.PARSE_FAILED.name());
        message.setErrorCode(errorCode);
        message.setErrorSummary(errorCode);
    }

    private MtImMessage findDuplicate(MtImMessage message) {
        MtImMessage byCallback = mtImMessageMapper.selectByCallbackMsgId(message.getCallbackMsgId());
        if (byCallback != null) {
            return byCallback;
        }
        if (message.getAppId() != null && message.getPlatformMsgId() != null) {
            return mtImMessageMapper.selectByPlatformMessage(message.getAppId(), message.getPlatformMsgId());
        }
        return null;
    }

    private Long firstLong(JsonNode primary, JsonNode fallback, String field) {
        Long value = longValue(primary, field);
        return value != null ? value : longValue(fallback, field);
    }

    private Integer firstInteger(JsonNode primary, JsonNode fallback, String field) {
        Integer value = integerValue(primary, field);
        return value != null ? value : integerValue(fallback, field);
    }

    private String firstText(JsonNode primary, JsonNode fallback, String field) {
        String value = textValue(primary, field);
        return StringUtils.isNotBlank(value) ? value : textValue(fallback, field);
    }

    private Long longValue(JsonNode node, String field) {
        return node == null ? null : parseLong(node.path(field).asText(null));
    }

    private Integer integerValue(JsonNode node, String field) {
        return node == null ? null : parseInteger(node.path(field).asText(null));
    }

    private String textValue(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private String jsonOrText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private Long parseLong(String value) {
        try {
            return StringUtils.isBlank(value) ? null : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return StringUtils.isBlank(value) ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String safeId(String value) {
        return StringUtils.left(StringUtils.defaultString(value), 128);
    }

    private record VerificationResult(boolean valid, String errorCode) {
        static VerificationResult success() {
            return new VerificationResult(true, null);
        }

        static VerificationResult failure(String errorCode) {
            return new VerificationResult(false, errorCode);
        }
    }

    public record IngestionResult(boolean success, boolean duplicate, Long recordId, String errorCode) {
        static IngestionResult success(Long recordId) {
            return new IngestionResult(true, false, recordId, null);
        }

        static IngestionResult duplicate(Long recordId) {
            return new IngestionResult(true, true, recordId, null);
        }

        static IngestionResult failure(String errorCode) {
            return new IngestionResult(false, false, null, errorCode);
        }
    }
}
