package com.gogoga.backend.client;

import com.gogoga.backend.dto.MtImCreateGroupResultDTO;
import com.gogoga.backend.dto.MtImSendMessageResultDTO;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.im.createGroup.CreateGroupRequest;
import com.meituan.sdk.model.wmoperNg.im.createGroup.CreateGroupResponse;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.PushContent;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.SendImMsgWmoperRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtCommentImClientTest {

    @Mock
    private MeituanClient meituanClient;

    private MtCommentImClient client;

    @BeforeEach
    void setUp() {
        client = new MtCommentImClient(meituanClient);
    }

    @Test
    void createsCommentGroupWithFixedGroupTypeAndPersistsSafeSummary() throws Exception {
        CreateGroupResponse data = new CreateGroupResponse();
        data.setGroupId(9988L);
        data.setNewGroup("true");
        data.setCtime(1784034347L);
        data.setState(1);
        data.setCloseType("NONE");
        MeituanResponse<CreateGroupResponse> response = success(data);
        when(meituanClient.invokeApi(any(CreateGroupRequest.class), eq("access-token"))).thenReturn(response);

        MtImCreateGroupResultDTO result = client.createCommentGroup("access-token", 542543250001L);

        ArgumentCaptor<CreateGroupRequest> captor = ArgumentCaptor.forClass(CreateGroupRequest.class);
        verify(meituanClient, times(1)).invokeApi(captor.capture(), eq("access-token"));
        assertEquals(1, captor.getValue().getGroupType());
        assertEquals(542543250001L, captor.getValue().getCommentId());
        assertEquals(9988L, result.getGroupId());
        assertTrue(result.getResponseSummary().contains("OP_SUCCESS"));
        assertFalse(result.getResponseSummary().contains("access-token"));
    }

    @Test
    void rejectsSuccessfulCreateResponseWithoutGroupIdWithoutRetry() throws Exception {
        when(meituanClient.invokeApi(any(CreateGroupRequest.class), eq("access-token")))
                .thenReturn(success(new CreateGroupResponse()));

        assertThrows(IllegalStateException.class,
                () -> client.createCommentGroup("access-token", 542543250001L));

        verify(meituanClient, times(1)).invokeApi(any(CreateGroupRequest.class), eq("access-token"));
    }

    @Test
    void mapsAllFixedSendFieldsAndCallsSdkExactlyOnce() throws Exception {
        MeituanResponse<Void> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("成功");
        when(meituanClient.invokeApi(any(SendImMsgWmoperRequest.class), eq("access-token"))).thenReturn(response);

        MtImSendMessageResultDTO result = client.sendCommentMessage(
                "access-token", 542543250001L, 9988L, 1784034347000001L, 1784034347L, "encrypted-base64");

        ArgumentCaptor<SendImMsgWmoperRequest> captor = ArgumentCaptor.forClass(SendImMsgWmoperRequest.class);
        verify(meituanClient, times(1)).invokeApi(captor.capture(), eq("access-token"));
        SendImMsgWmoperRequest request = captor.getValue();
        PushContent content = request.getPushContent();
        assertEquals(4, request.getBizType());
        assertEquals(1784034347000001L, content.getMsgId());
        assertEquals("encrypted-base64", content.getMsgContent());
        assertEquals(0, content.getMsgType());
        assertEquals(1, content.getMsgSource());
        assertEquals(1784034347L, content.getCts());
        assertEquals(-1, content.getSceneTag());
        assertEquals(542543250001L, content.getCommentId());
        assertEquals(9988L, content.getGroupId());
        assertFalse(result.getResponseSummary().contains("encrypted-base64"));
        assertFalse(result.getResponseSummary().contains("access-token"));
    }

    @Test
    void surfacesSdkSendFailureAfterOneAttempt() throws Exception {
        when(meituanClient.invokeApi(any(SendImMsgWmoperRequest.class), eq("access-token")))
                .thenThrow(new MtSdkException("SDK_REQUEST_FAILED", "timeout"));

        assertThrows(IllegalStateException.class, () -> client.sendCommentMessage(
                "access-token", 542543250001L, 9988L, 1L, 1784034347L, "encrypted-base64"));

        verify(meituanClient, times(1)).invokeApi(any(SendImMsgWmoperRequest.class), eq("access-token"));
    }

    private MeituanResponse<CreateGroupResponse> success(CreateGroupResponse data) {
        MeituanResponse<CreateGroupResponse> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setMsg("成功");
        response.setData(data);
        return response;
    }
}
