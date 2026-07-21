package com.gogoga.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.service.MtImMessageCryptoService;
import com.gogoga.backend.service.SensitiveWordService;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.SendImMsgWmoperRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtImMessageClientTest {

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtImMessageCryptoService cryptoService;

    @Mock
    private SensitiveWordService sensitiveWordService;

    @InjectMocks
    private MtImMessageClient client;

    @Test
    void sendsBizTypeOneMerchantMessageWithEncryptedContentAndDefaults() throws Exception {
        when(cryptoService.encrypt("reply-content")).thenReturn("encrypted-content");
        when(meituanClient.invokeApi(any(SendImMsgWmoperRequest.class), eq("token")))
                .thenReturn(successResponse());

        MtImMessageClient.SendResult result = client.sendTwoPartyMessage(
                "token",
                command(1, null, null)
        );

        assertTrue(result.success());
        ArgumentCaptor<SendImMsgWmoperRequest> request =
                ArgumentCaptor.forClass(SendImMsgWmoperRequest.class);
        verify(meituanClient).invokeApi(request.capture(), eq("token"));
        verify(cryptoService).encrypt("reply-content");
        verify(sensitiveWordService).detect("reply-content");

        JsonNode payload = JsonUtil.readTree(request.getValue().serializeToJson());
        JsonNode pushContent = payload.path("push_content");
        assertEquals(1, payload.path("biz_type").asInt());
        assertEquals(1888888888888888888L, pushContent.path("msg_id").asLong());
        assertEquals("encrypted-content", pushContent.path("msg_content").asText());
        assertFalse(request.getValue().serializeToJson().contains("reply-content"));
        assertEquals(1, pushContent.path("msg_type").asInt());
        assertEquals(1, pushContent.path("msg_source").asInt());
        assertEquals(1784374000L, pushContent.path("cts").asLong());
        assertEquals(0L, pushContent.path("order_id").asLong());
        assertEquals(11158056890L, pushContent.path("open_user_id").asLong());
        assertEquals(-1, pushContent.path("scene_tag").asInt());
        assertEquals("", pushContent.path("scene_name").asText());
        assertEquals(2, pushContent.path("has_reply").asInt());
        assertFalse(pushContent.has("comment_id"));
        assertFalse(pushContent.has("group_id"));
        assertFalse(result.responseSummary().contains("token"));
        assertFalse(result.responseSummary().contains("reply-content"));
        assertFalse(result.responseSummary().contains("encrypted-content"));
        verifyNoMoreInteractions(meituanClient, cryptoService, sensitiveWordService);
    }

    @Test
    void supportsImageAndOtherMessageTypes() throws Exception {
        when(cryptoService.encrypt("reply-content")).thenReturn("encrypted-content");
        when(meituanClient.invokeApi(any(SendImMsgWmoperRequest.class), eq("token")))
                .thenReturn(successResponse());

        assertTrue(client.sendTwoPartyMessage("token", command(2, 8, "售后场景")).success());
        assertTrue(client.sendTwoPartyMessage("token", command(3, -1, "")).success());

        ArgumentCaptor<SendImMsgWmoperRequest> requests =
                ArgumentCaptor.forClass(SendImMsgWmoperRequest.class);
        verify(meituanClient, org.mockito.Mockito.times(2)).invokeApi(requests.capture(), eq("token"));
        assertEquals(2, requests.getAllValues().get(0).getPushContent().getMsgType());
        assertEquals(8, requests.getAllValues().get(0).getPushContent().getSceneTag());
        assertEquals("售后场景", requests.getAllValues().get(0).getPushContent().getSceneName());
        assertEquals(3, requests.getAllValues().get(1).getPushContent().getMsgType());
        verify(cryptoService, org.mockito.Mockito.times(2)).encrypt("reply-content");
        verifyNoInteractions(sensitiveWordService);
        verifyNoMoreInteractions(meituanClient, cryptoService, sensitiveWordService);
    }

    @Test
    void rejectsCardAndUnknownTypesBeforeEncryptionOrSdkCall() {
        IllegalArgumentException card = assertThrows(
                IllegalArgumentException.class,
                () -> client.sendTwoPartyMessage("token", command(17, null, null))
        );
        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> client.sendTwoPartyMessage("token", command(99, null, null))
        );

        assertEquals("MT_IM_MESSAGE_TYPE_UNSUPPORTED", card.getMessage());
        assertEquals("MT_IM_MESSAGE_TYPE_UNSUPPORTED", unknown.getMessage());
        verifyNoInteractions(meituanClient, cryptoService, sensitiveWordService);
    }

    @Test
    void blocksSensitiveTextBeforeEncryptionAndSdkCallWithSafeSummary() {
        SensitiveWordHitDTO hit = new SensitiveWordHitDTO();
        hit.setHit(true);
        hit.setCategories(java.util.List.of("站外导流违规词"));
        hit.setHitCount(1);
        hit.setHighestSeverity("HIGH");
        when(sensitiveWordService.detect("请加 微-信 处理")).thenReturn(hit);

        MtImMessageClient.SendResult result = client.sendTwoPartyMessage(
                "token-secret",
                command(1, null, null, "请加 微-信 处理")
        );

        assertFalse(result.success());
        assertEquals("SENSITIVE_WORD_HIT", result.errorCode());
        assertTrue(result.responseSummary().contains("站外导流违规词"));
        assertTrue(result.responseSummary().contains("HIGH"));
        assertFalse(result.responseSummary().contains("微信"));
        assertFalse(result.responseSummary().contains("请加"));
        assertFalse(result.responseSummary().contains("token-secret"));
        verify(sensitiveWordService).detect("请加 微-信 处理");
        verifyNoInteractions(meituanClient, cryptoService);
        verifyNoMoreInteractions(sensitiveWordService);
    }

    @Test
    void returnsSafeUpstreamFailureWithoutRetry() throws Exception {
        when(cryptoService.encrypt("reply-content")).thenReturn("encrypted-content");
        MeituanResponse<Void> response = new MeituanResponse<>();
        response.setCode("UPSTREAM_FAILED");
        response.setMsg("token-secret reply-content encrypted-content");
        when(meituanClient.invokeApi(any(SendImMsgWmoperRequest.class), eq("token-secret")))
                .thenReturn(response);

        MtImMessageClient.SendResult result = client.sendTwoPartyMessage(
                "token-secret",
                command(1, null, null)
        );

        assertFalse(result.success());
        assertEquals("UPSTREAM_FAILED", result.errorCode());
        assertFalse(result.responseSummary().contains("token-secret"));
        assertFalse(result.responseSummary().contains("reply-content"));
        assertFalse(result.responseSummary().contains("encrypted-content"));
        verify(meituanClient).invokeApi(any(SendImMsgWmoperRequest.class), eq("token-secret"));
        verify(sensitiveWordService).detect("reply-content");
        verifyNoMoreInteractions(meituanClient, sensitiveWordService);
    }

    @Test
    void returnsSdkErrorAfterOneAttempt() throws Exception {
        when(cryptoService.encrypt("reply-content")).thenReturn("encrypted-content");
        when(meituanClient.invokeApi(any(SendImMsgWmoperRequest.class), eq("token")))
                .thenThrow(new MtSdkException("SDK_REQUEST_FAILED", "boom"));

        MtImMessageClient.SendResult result = client.sendTwoPartyMessage("token", command(1, null, null));

        assertFalse(result.success());
        assertEquals("SDK_ERROR", result.errorCode());
        verify(meituanClient).invokeApi(any(SendImMsgWmoperRequest.class), eq("token"));
        verify(sensitiveWordService).detect("reply-content");
        verifyNoMoreInteractions(meituanClient, sensitiveWordService);
    }

    private MtImMessageClient.TwoPartyMessageCommand command(int messageType,
                                                              Integer sceneTag,
                                                              String sceneName) {
        return command(messageType, sceneTag, sceneName, "reply-content");
    }

    private MtImMessageClient.TwoPartyMessageCommand command(int messageType,
                                                              Integer sceneTag,
                                                              String sceneName,
                                                              String content) {
        return new MtImMessageClient.TwoPartyMessageCommand(
                1888888888888888888L,
                content,
                messageType,
                1784374000L,
                0L,
                11158056890L,
                sceneTag,
                sceneName,
                "extension"
        );
    }

    private MeituanResponse<Void> successResponse() {
        MeituanResponse<Void> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        return response;
    }
}
