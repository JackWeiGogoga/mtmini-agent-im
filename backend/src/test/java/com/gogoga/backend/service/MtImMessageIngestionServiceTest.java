package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.ImMessageDecryptStatusEnum;
import com.gogoga.backend.enums.ImMessageParseStatusEnum;
import com.gogoga.backend.mapper.MtImMessageMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.meituan.sdk.internal.utils.SignerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtImMessageIngestionServiceTest {

    private static final String SIGN_KEY = "sign-key";

    @Mock private MtImMessageMapper messageMapper;
    @Mock private MtShopMapper shopMapper;
    @Mock private MtImMessageCryptoService cryptoService;
    @Mock private MtImConversationService conversationService;
    @Mock private MtImAutoReplyEnqueueService autoReplyEnqueueService;

    private MtImMessageIngestionService service;

    @BeforeEach
    void setUp() {
        MtProperties properties = new MtProperties();
        properties.setSignKey(SIGN_KEY);
        properties.setDeveloperId(123L);
        // 故意与回调 businessId 取不同值，钉住两者互不影响
        properties.setBusinessId(16);
        properties.getIm().setCallbackBusinessId(1);
        service = new MtImMessageIngestionService(
                properties, messageMapper, shopMapper, cryptoService, conversationService, autoReplyEnqueueService);
    }

    @Test
    void verifiesParsesStringPushContentDecryptsAndPersistsAllMessageValues() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(88L);
        when(shopMapper.selectByEpoiId("17839950841")).thenReturn(shop);
        when(cryptoService.decrypt("cipher-text")).thenReturn("顾客消息");
        when(cryptoService.sha256("顾客消息")).thenReturn("hash");
        when(messageMapper.insert(any())).thenAnswer(invocation -> {
            ((MtImMessage) invocation.getArgument(0)).setId(9L);
            return 1;
        });
        String push = "{\"app_id\":9637,\"app_poi_code\":\"17839950841\",\"cts\":1784374000,"
                + "\"has_reply\":1,\"msg_content\":\"cipher-text\",\"msg_id\":1772597840435073024,"
                + "\"msg_source\":2,\"msg_type\":99,\"open_user_id\":11158056890,\"order_id\":0,"
                + "\"scene_name\":\"\",\"scene_tag\":-1,\"future_field\":\"kept\"}";
        String envelope = "{\"biz_type\":1,\"sig\":\"inner-storage-only\",\"timestamp\":1784374003,"
                + "\"extended_field\":{\"x\":1},\"push_content\":" + quoteJson(push) + "}";

        MtImMessageIngestionService.IngestionResult result = service.ingest(signedParameters("callback-1", envelope));

        assertTrue(result.success());
        ArgumentCaptor<MtImMessage> saved = ArgumentCaptor.forClass(MtImMessage.class);
        verify(messageMapper).insert(saved.capture());
        MtImMessage message = saved.getValue();
        assertEquals(88L, message.getShopId());
        assertEquals(2, message.getMsgSource());
        assertEquals(99, message.getMsgType());
        assertEquals(1, message.getHasReply());
        assertEquals(0L, message.getOrderId());
        assertEquals("cipher-text", message.getMsgContentCiphertext());
        assertEquals("顾客消息", message.getMsgContentPlaintext());
        assertEquals("hash", message.getMsgContentHash());
        assertEquals(ImMessageParseStatusEnum.SUCCESS.name(), message.getParseStatus());
        assertEquals(ImMessageDecryptStatusEnum.SUCCESS.name(), message.getDecryptStatus());
        assertTrue(message.getRawPushContentJson().contains("future_field"));
    }

    @Test
    void acceptsObjectPushContentAndPersistsUnmatchedShopAsSuccessfulCallback() throws Exception {
        String envelope = "{\"biz_type\":1,\"push_content\":{\"app_id\":9637,\"msg_id\":42,"
                + "\"app_poi_code\":\"unknown\",\"msg_source\":1,\"msg_type\":1,\"has_reply\":0}}";

        MtImMessageIngestionService.IngestionResult result = service.ingest(signedParameters("callback-2", envelope));

        assertTrue(result.success());
        ArgumentCaptor<MtImMessage> saved = ArgumentCaptor.forClass(MtImMessage.class);
        verify(messageMapper).insert(saved.capture());
        assertNull(saved.getValue().getShopId());
        assertEquals(ImMessageParseStatusEnum.SHOP_UNMATCHED.name(), saved.getValue().getParseStatus());
    }

    @Test
    void rejectsInvalidOuterSignatureBeforeParsingOrPersistence() throws Exception {
        Map<String, String> parameters = signedParameters("callback-3", "{\"biz_type\":1}");
        parameters.put("sign", "invalid");

        MtImMessageIngestionService.IngestionResult result = service.ingest(parameters);

        assertFalse(result.success());
        assertEquals("CALLBACK_SIGN_INVALID", result.errorCode());
        verify(messageMapper, never()).insert(any());
        verify(cryptoService, never()).decrypt(any());
    }

    @Test
    void returnsDuplicateForEitherIdempotencyKey() throws Exception {
        MtImMessage existing = new MtImMessage();
        existing.setId(7L);
        when(messageMapper.selectByCallbackMsgId("callback-4")).thenReturn(existing);

        MtImMessageIngestionService.IngestionResult result = service.ingest(
                signedParameters("callback-4", "{\"biz_type\":1}"));

        assertTrue(result.success());
        assertTrue(result.duplicate());
        verify(messageMapper, never()).insert(any());
    }

    @Test
    void returnsDuplicateWhenPlatformMessageWasDeliveredWithAnotherCallbackId() throws Exception {
        MtImMessage existing = new MtImMessage();
        existing.setId(8L);
        when(messageMapper.selectByPlatformMessage(9637L, 42L)).thenReturn(existing);
        String envelope = "{\"biz_type\":1,\"push_content\":{\"app_id\":9637,\"msg_id\":42}}";

        MtImMessageIngestionService.IngestionResult result = service.ingest(
                signedParameters("callback-platform-duplicate", envelope));

        assertTrue(result.success());
        assertTrue(result.duplicate());
        assertEquals(8L, result.recordId());
        verify(messageMapper, never()).insert(any());
    }

    @Test
    void mergesMeituanEchoIntoExistingInternalAiMessage() throws Exception {
        MtImMessage existing = new MtImMessage();
        existing.setId(8L);
        existing.setRecordSource("AI_AUTO_REPLY");
        when(messageMapper.selectByPlatformMessage(9637L, 42L)).thenReturn(existing);
        String envelope = "{\"biz_type\":1,\"push_content\":{\"app_id\":9637,\"msg_id\":42}}";

        MtImMessageIngestionService.IngestionResult result = service.ingest(
                signedParameters("callback-ai-echo", envelope));

        assertTrue(result.duplicate());
        verify(messageMapper).mergeCallbackIntoInternalMessage(any());
        verify(messageMapper, never()).insert(any());
    }

    @Test
    void persistsDecryptFailureButPropagatesDatabaseFailure() throws Exception {
        MtShop shop = new MtShop();
        shop.setId(88L);
        when(shopMapper.selectByEpoiId("poi")).thenReturn(shop);
        when(cryptoService.decrypt("bad-cipher")).thenThrow(new IllegalStateException("secret detail"));
        String envelope = "{\"biz_type\":1,\"push_content\":{\"app_id\":1,\"msg_id\":2,"
                + "\"app_poi_code\":\"poi\",\"msg_content\":\"bad-cipher\"}}";

        MtImMessageIngestionService.IngestionResult result = service.ingest(signedParameters("callback-5", envelope));

        assertTrue(result.success());
        ArgumentCaptor<MtImMessage> saved = ArgumentCaptor.forClass(MtImMessage.class);
        verify(messageMapper).insert(saved.capture());
        assertEquals(ImMessageDecryptStatusEnum.FAILED.name(), saved.getValue().getDecryptStatus());
        assertEquals("MESSAGE_DECRYPT_FAILED", saved.getValue().getErrorSummary());

        when(messageMapper.selectByCallbackMsgId("callback-6")).thenReturn(null);
        when(messageMapper.insert(any())).thenThrow(new TransientDataAccessResourceException("db unavailable"));
        assertThrows(TransientDataAccessResourceException.class,
                () -> service.ingest(signedParameters("callback-6", envelope)));
    }

    @Test
    void rejectsCallbackCarryingOauthBusinessIdInsteadOfCallbackBusinessId() throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("msgType", "210069");
        parameters.put("developerId", "123");
        parameters.put("businessId", "16");
        parameters.put("msgId", "callback-oauth-business");
        parameters.put("timestamp", "1784374003");
        parameters.put("sign", SignerUtil.getSign(SIGN_KEY, parameters));

        MtImMessageIngestionService.IngestionResult result = service.ingest(parameters);

        assertFalse(result.success());
        assertEquals("CALLBACK_BUSINESS_MISMATCH", result.errorCode());
        verify(messageMapper, never()).insert(any());
    }

    private Map<String, String> signedParameters(String callbackMsgId, String message) throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("msgType", "210069");
        parameters.put("developerId", "123");
        parameters.put("businessId", "1");
        parameters.put("msgId", callbackMsgId);
        parameters.put("timestamp", "1784374003");
        parameters.put("opBizCode", "op-1");
        parameters.put("ePoiId", "outer-poi");
        parameters.put("message", message);
        parameters.put("sign", SignerUtil.getSign(SIGN_KEY, parameters));
        return parameters;
    }

    private String quoteJson(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
