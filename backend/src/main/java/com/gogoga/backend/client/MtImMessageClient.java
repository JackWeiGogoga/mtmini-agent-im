package com.gogoga.backend.client;

import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.enums.ImMessageTypeEnum;
import com.gogoga.backend.service.MtImMessageCryptoService;
import com.gogoga.backend.service.SensitiveWordService;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.PushContent;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.SendImMsgWmoperRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 美团两方会话消息发送客户端。
 *
 * <p>只负责构造并单次发送 {@code biz_type=1} 的商家消息，不生成回复内容、不创建消息 ID、
 * 不扣减算力、不查询远端状态，也不在客户端内部重试。</p>
 */
@Component
@RequiredArgsConstructor
public class MtImMessageClient {

    private static final int BIZ_TYPE_TWO_PARTY = 1;
    private static final int MSG_SOURCE_MERCHANT = 1;
    private static final int HAS_REPLIED = 2;
    private static final int DEFAULT_SCENE_TAG = -1;
    private static final long MIN_TEN_DIGIT_TIMESTAMP = 1_000_000_000L;
    private static final long MAX_TEN_DIGIT_TIMESTAMP = 9_999_999_999L;

    private final MeituanClient meituanClient;
    private final MtImMessageCryptoService cryptoService;
    private final SensitiveWordService sensitiveWordService;

    public SendResult sendTwoPartyMessage(String accessToken, TwoPartyMessageCommand command) {
        validate(accessToken, command);
        if (command.messageType() == ImMessageTypeEnum.TEXT.getCode()) {
            SensitiveWordHitDTO hit = sensitiveWordService.detect(command.plaintextContent());
            if (hit != null && hit.isHit()) {
                return SendResult.failure("SENSITIVE_WORD_HIT", sensitiveWordSummary(hit));
            }
        }

        PushContent pushContent = new PushContent();
        pushContent.setMsgId(command.messageId());
        pushContent.setMsgContent(cryptoService.encrypt(command.plaintextContent()));
        pushContent.setMsgType(command.messageType());
        pushContent.setMsgSource(MSG_SOURCE_MERCHANT);
        pushContent.setCts(command.cts());
        pushContent.setOrderId(command.orderId());
        pushContent.setOpenUserId(command.openUserId());
        pushContent.setSceneTag(command.sceneTag() == null ? DEFAULT_SCENE_TAG : command.sceneTag());
        pushContent.setSceneName(StringUtils.defaultString(command.sceneName()));
        pushContent.setHasReply(HAS_REPLIED);

        SendImMsgWmoperRequest request = new SendImMsgWmoperRequest();
        request.setBizType(BIZ_TYPE_TWO_PARTY);
        request.setPushContent(pushContent);
        request.setExtendedField(command.extendedField());

        try {
            MeituanResponse<Void> response = meituanClient.invokeApi(request, accessToken);
            if (response == null) {
                return SendResult.failure("EMPTY_RESPONSE", null);
            }
            String summary = responseSummary(response);
            if (!response.isSuccess()) {
                return SendResult.failure(
                        StringUtils.defaultIfBlank(response.getCode(), "UPSTREAM_FAILED"),
                        summary
                );
            }
            return SendResult.success(summary);
        } catch (MtSdkException e) {
            return SendResult.failure("SDK_ERROR", null);
        }
    }

    private void validate(String accessToken, TwoPartyMessageCommand command) {
        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("MT_IM_ACCESS_TOKEN_MISSING");
        }
        if (command == null) {
            throw new IllegalArgumentException("MT_IM_SEND_COMMAND_MISSING");
        }
        if (command.messageId() == null || command.messageId() <= 0) {
            throw new IllegalArgumentException("MT_IM_MESSAGE_ID_INVALID");
        }
        if (StringUtils.isBlank(command.plaintextContent())) {
            throw new IllegalArgumentException("IM_MESSAGE_PLAINTEXT_EMPTY");
        }
        ImMessageTypeEnum messageType = ImMessageTypeEnum.fromCode(command.messageType())
                .orElseThrow(() -> new IllegalArgumentException("MT_IM_MESSAGE_TYPE_UNSUPPORTED"));
        if (!messageType.isSendSupported()) {
            throw new IllegalArgumentException("MT_IM_MESSAGE_TYPE_UNSUPPORTED");
        }
        if (command.cts() == null
                || command.cts() < MIN_TEN_DIGIT_TIMESTAMP
                || command.cts() > MAX_TEN_DIGIT_TIMESTAMP) {
            throw new IllegalArgumentException("MT_IM_MESSAGE_CTS_INVALID");
        }
        if (command.orderId() == null || command.orderId() < 0) {
            throw new IllegalArgumentException("MT_IM_ORDER_ID_INVALID");
        }
        if (command.openUserId() == null || command.openUserId() <= 0) {
            throw new IllegalArgumentException("MT_IM_OPEN_USER_ID_INVALID");
        }
    }

    private String responseSummary(MeituanResponse<?> response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("code", response.getCode());
        summary.put("traceId", response.getTraceId());
        summary.put("dataPresent", response.getData() != null);
        return JsonUtil.toJson(summary);
    }

    private String sensitiveWordSummary(SensitiveWordHitDTO hit) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("categories", hit.getCategories());
        summary.put("hitCount", hit.getHitCount());
        summary.put("highestSeverity", hit.getHighestSeverity());
        return JsonUtil.toJson(summary);
    }

    /**
     * 调用方必须生成稳定且唯一的 messageId；重试同一条业务消息时必须复用该 ID。
     */
    public record TwoPartyMessageCommand(Long messageId,
                                         String plaintextContent,
                                         Integer messageType,
                                         Long cts,
                                         Long orderId,
                                         Long openUserId,
                                         Integer sceneTag,
                                         String sceneName,
                                         String extendedField) {
    }

    public record SendResult(boolean success, String errorCode, String responseSummary) {

        static SendResult success(String responseSummary) {
            return new SendResult(true, null, responseSummary);
        }

        static SendResult failure(String errorCode, String responseSummary) {
            return new SendResult(false, errorCode, responseSummary);
        }
    }
}
