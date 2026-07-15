package com.gogoga.backend.client;

import com.gogoga.backend.dto.MtImCreateGroupResultDTO;
import com.gogoga.backend.dto.MtImSendMessageResultDTO;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.im.createGroup.CreateGroupRequest;
import com.meituan.sdk.model.wmoperNg.im.createGroup.CreateGroupResponse;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.PushContent;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.SendImMsgWmoperRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 美团评价匿名群聊客户端。调用层不做重试。
 */
@Component
@RequiredArgsConstructor
public class MtCommentImClient {

    private static final int GROUP_TYPE_COMMENT = 1;
    private static final int BIZ_TYPE_ANONYMOUS_COMMENT_GROUP = 4;
    private final MeituanClient meituanClient;

    public MtImCreateGroupResultDTO createCommentGroup(String accessToken, Long commentId) {
        requireContext(accessToken, commentId);
        CreateGroupRequest request = new CreateGroupRequest();
        request.setGroupType(GROUP_TYPE_COMMENT);
        request.setCommentId(commentId);
        try {
            MeituanResponse<CreateGroupResponse> response = meituanClient.invokeApi(request, accessToken);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("MT_IM_CREATE_GROUP_FAILED:" + safeMessage(response));
            }
            CreateGroupResponse data = response.getData();
            if (data == null || data.getGroupId() == null) {
                throw new IllegalStateException("MT_IM_CREATE_GROUP_MISSING_GROUP_ID");
            }
            MtImCreateGroupResultDTO result = new MtImCreateGroupResultDTO();
            result.setGroupId(data.getGroupId());
            result.setNewGroup(data.getNewGroup());
            result.setCtime(data.getCtime());
            result.setState(data.getState());
            result.setCloseType(data.getCloseType());
            result.setResponseSummary(responseSummary(response));
            return result;
        } catch (MtSdkException e) {
            throw new IllegalStateException("MT_IM_CREATE_GROUP_SDK_ERROR", e);
        }
    }

    public MtImSendMessageResultDTO sendCommentMessage(String accessToken, Long commentId, Long groupId,
                                                        Long messageId, Long cts, String encryptedContent) {
        requireContext(accessToken, commentId);
        if (groupId == null || messageId == null || cts == null || StringUtils.isBlank(encryptedContent)) {
            throw new IllegalArgumentException("MT_IM_SEND_CONTEXT_INVALID");
        }
        PushContent pushContent = new PushContent();
        pushContent.setMsgId(messageId);
        pushContent.setMsgContent(encryptedContent);
        pushContent.setMsgType(0);
        pushContent.setMsgSource(1);
        pushContent.setCts(cts);
        pushContent.setSceneTag(-1);
        pushContent.setCommentId(commentId);
        pushContent.setGroupId(groupId);

        SendImMsgWmoperRequest request = new SendImMsgWmoperRequest();
        request.setBizType(BIZ_TYPE_ANONYMOUS_COMMENT_GROUP);
        request.setPushContent(pushContent);
        try {
            MeituanResponse<Void> response = meituanClient.invokeApi(request, accessToken);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("MT_IM_SEND_FAILED:" + safeMessage(response));
            }
            MtImSendMessageResultDTO result = new MtImSendMessageResultDTO();
            result.setResponseSummary(responseSummary(response));
            return result;
        } catch (MtSdkException e) {
            throw new IllegalStateException("MT_IM_SEND_SDK_ERROR", e);
        }
    }

    private void requireContext(String accessToken, Long commentId) {
        if (StringUtils.isBlank(accessToken) || commentId == null) {
            throw new IllegalArgumentException("MT_IM_CLIENT_CONTEXT_INVALID");
        }
    }

    private String safeMessage(MeituanResponse<?> response) {
        return response == null ? "EMPTY_RESPONSE" : StringUtils.defaultIfBlank(response.getMsg(), response.getCode());
    }

    private String responseSummary(MeituanResponse<?> response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("code", response.getCode());
        summary.put("msg", response.getMsg());
        summary.put("traceId", response.getTraceId());
        return JsonUtil.toJson(summary);
    }
}
