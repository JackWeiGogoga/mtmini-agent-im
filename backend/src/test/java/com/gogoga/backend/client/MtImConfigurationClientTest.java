package com.gogoga.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.im.setAutoSendConfigInfo.SetAutoSendConfigInfoRequest;
import com.meituan.sdk.model.wmoperNg.im.setPoiImStatus.SetPoiImStatusRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtImConfigurationClientTest {

    @Mock
    private MeituanClient meituanClient;

    @InjectMocks
    private MtImConfigurationClient client;

    @Test
    void enablesPoiImWithFixedStatusOne() throws Exception {
        when(meituanClient.invokeApi(any(SetPoiImStatusRequest.class), eq("token")))
                .thenReturn(successResponse());

        MtImConfigurationClient.ImConfigurationResult result = client.enablePoiIm("token");

        assertTrue(result.success());
        ArgumentCaptor<SetPoiImStatusRequest> request = ArgumentCaptor.forClass(SetPoiImStatusRequest.class);
        verify(meituanClient).invokeApi(request.capture(), eq("token"));
        JsonNode payload = JsonUtil.readTree(request.getValue().serializeToJson());
        assertEquals(1, payload.path("imStatus").asInt());
        assertFalse(result.responseSummary().contains("token"));
        verifyNoMoreInteractions(meituanClient);
    }

    @Test
    void disablesAllSmartReplySwitchesWithOneFixedArrayItem() throws Exception {
        when(meituanClient.invokeApi(any(SetAutoSendConfigInfoRequest.class), eq("token")))
                .thenReturn(successResponse());

        MtImConfigurationClient.ImConfigurationResult result = client.disableAllSmartReplies("token");

        assertTrue(result.success());
        ArgumentCaptor<SetAutoSendConfigInfoRequest> request =
                ArgumentCaptor.forClass(SetAutoSendConfigInfoRequest.class);
        verify(meituanClient).invokeApi(request.capture(), eq("token"));
        JsonNode payload = JsonUtil.readTree(request.getValue().serializeToJson());
        assertEquals(1, payload.path("allStatus").size());
        assertEquals(0, payload.path("allStatus").get(0).path("type").asInt());
        assertEquals(0, payload.path("allStatus").get(0).path("switchStatus").asInt());
        verifyNoMoreInteractions(meituanClient);
    }

    @Test
    void returnsSafeFailureWithoutClientRetry() throws Exception {
        MeituanResponse<String> response = new MeituanResponse<>();
        response.setCode("UPSTREAM_FAILED");
        response.setMsg("do not expose token token-secret");
        when(meituanClient.invokeApi(any(SetPoiImStatusRequest.class), eq("token-secret")))
                .thenReturn(response);

        MtImConfigurationClient.ImConfigurationResult result = client.enablePoiIm("token-secret");

        assertFalse(result.success());
        assertEquals("UPSTREAM_FAILED", result.errorCode());
        assertFalse(result.responseSummary().contains("token-secret"));
        verify(meituanClient).invokeApi(any(SetPoiImStatusRequest.class), eq("token-secret"));
        verifyNoMoreInteractions(meituanClient);
    }

    private MeituanResponse<String> successResponse() {
        MeituanResponse<String> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setData("ok");
        return response;
    }
}
