package com.gogoga.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.client.request.MtQueryBlockMsgListRequest;
import com.gogoga.backend.client.request.MtSetBlockMsgListRequest;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtUpdateBlockMsgListClientTest {

    @Mock
    private MeituanClient meituanClient;

    @InjectMocks
    private MtUpdateBlockMsgListClient client;

    @Test
    void shouldSendSdkRequestWithExpectedCloseBizPayload() throws Exception {
        MeituanResponse<Object> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        when(meituanClient.invokeApi(any(MtSetBlockMsgListRequest.class), eq("access-token"))).thenReturn(response);

        MtUpdateBlockMsgListClient.UpdateBlockMsgListResult result =
                client.blockOrderPushResult("access-token", "epoi-001");

        assertTrue(result.success());
        ArgumentCaptor<MtSetBlockMsgListRequest> requestCaptor = ArgumentCaptor.forClass(MtSetBlockMsgListRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("access-token"));

        JsonNode biz = JsonUtil.readTree(requestCaptor.getValue().serializeToJson());
        assertNotNull(biz);
        assertEquals("access-token", biz.path("targetAppAuthToken").asText());
        assertEquals(16, biz.path("targetBusinessId").asInt());
        assertEquals(16102, biz.path("blockMsgList").get(0).asInt());
    }

    @Test
    void shouldSendSdkRequestWithEmptyBlockMsgListWhenUnblockOrderPush() throws Exception {
        MeituanResponse<Object> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        when(meituanClient.invokeApi(any(MtSetBlockMsgListRequest.class), eq("access-token"))).thenReturn(response);

        MtUpdateBlockMsgListClient.UpdateBlockMsgListResult result =
                client.unblockOrderPushResult("access-token", "epoi-001");

        assertTrue(result.success());
        ArgumentCaptor<MtSetBlockMsgListRequest> requestCaptor = ArgumentCaptor.forClass(MtSetBlockMsgListRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("access-token"));

        JsonNode biz = JsonUtil.readTree(requestCaptor.getValue().serializeToJson());
        assertNotNull(biz);
        assertTrue(biz.path("blockMsgList").isArray());
        assertEquals(0, biz.path("blockMsgList").size());
    }

    @Test
    void shouldSendSdkRequestWithCustomBlockMsgList() throws Exception {
        MeituanResponse<Object> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        when(meituanClient.invokeApi(any(MtSetBlockMsgListRequest.class), eq("access-token"))).thenReturn(response);

        boolean success = client.updateBlockMsgList("access-token", "epoi-001", java.util.List.of(16102, 16103));

        assertTrue(success);
        ArgumentCaptor<MtSetBlockMsgListRequest> requestCaptor = ArgumentCaptor.forClass(MtSetBlockMsgListRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("access-token"));

        JsonNode biz = JsonUtil.readTree(requestCaptor.getValue().serializeToJson());
        assertNotNull(biz);
        assertEquals(2, biz.path("blockMsgList").size());
        assertEquals(16103, biz.path("blockMsgList").get(1).asInt());
    }

    @Test
    void shouldQueryBlockMsgListWhenMeituanReturnsSuccess() throws Exception {
        MtQueryBlockMsgListRequest.QueryBlockMsgListData data = new MtQueryBlockMsgListRequest.QueryBlockMsgListData();
        data.setBlockMsgList(java.util.List.of(16102));
        MeituanResponse<MtQueryBlockMsgListRequest.QueryBlockMsgListData> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        response.setData(data);
        when(meituanClient.invokeApi(any(MtQueryBlockMsgListRequest.class), eq("access-token"))).thenReturn(response);

        MtUpdateBlockMsgListClient.QueryBlockMsgListResult result = client.queryBlockMsgListResult("access-token", "epoi-001");

        assertTrue(result.success());
        assertEquals(java.util.List.of(16102), result.blockMsgList());
        verify(meituanClient).invokeApi(any(MtQueryBlockMsgListRequest.class), eq("access-token"));
    }

    @Test
    void shouldReturnFailureWhenQueryBlockMsgListResponseMissingData() throws Exception {
        MeituanResponse<MtQueryBlockMsgListRequest.QueryBlockMsgListData> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("success");
        when(meituanClient.invokeApi(any(MtQueryBlockMsgListRequest.class), eq("access-token"))).thenReturn(response);

        MtUpdateBlockMsgListClient.QueryBlockMsgListResult result = client.queryBlockMsgListResult("access-token", "epoi-001");

        assertFalse(result.success());
        assertEquals(java.util.Collections.emptyList(), result.blockMsgList());
    }

    @Test
    void shouldBlockOrderPushWhenMeituanReturnsSuccess() throws Exception {
        MeituanResponse<Object> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        when(meituanClient.invokeApi(any(MtSetBlockMsgListRequest.class), eq("access-token"))).thenReturn(response);

        MtUpdateBlockMsgListClient.UpdateBlockMsgListResult result =
                client.blockOrderPushResult("access-token", "epoi-001");

        assertTrue(result.success());
    }

    @Test
    void shouldReturnFalseWhenMeituanReturnsFailure() throws Exception {
        MeituanResponse<Object> response = new MeituanResponse<>();
        response.setCode("OP_FAILED");
        response.setMsg("failed");
        when(meituanClient.invokeApi(any(MtSetBlockMsgListRequest.class), eq("access-token"))).thenReturn(response);

        MtUpdateBlockMsgListClient.UpdateBlockMsgListResult result =
                client.blockOrderPushResult("access-token", "epoi-001");

        assertFalse(result.success());
    }

    @Test
    void shouldReturnFalseWhenAccessTokenBlank() throws Exception {
        boolean success = client.updateBlockMsgList(" ", "epoi-001", java.util.List.of(16102));

        assertFalse(success);
        verify(meituanClient, never()).invokeApi(any(MtSetBlockMsgListRequest.class), any(String.class));
    }

    @Test
    void shouldReturnFalseWhenSdkThrowsException() throws Exception {
        when(meituanClient.invokeApi(any(MtSetBlockMsgListRequest.class), eq("access-token")))
                .thenThrow(new MtSdkException("SDK_REQUEST_FAILED", "boom"));

        MtUpdateBlockMsgListClient.UpdateBlockMsgListResult result =
                client.blockOrderPushResult("access-token", "epoi-001");

        assertFalse(result.success());
    }
}
