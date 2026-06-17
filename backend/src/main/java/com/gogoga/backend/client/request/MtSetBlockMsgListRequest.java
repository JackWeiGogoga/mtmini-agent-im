package com.gogoga.backend.client.request;

import com.google.gson.reflect.TypeToken;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanRequest;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.annotations.ApiMeta;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * 关闭美团消息提醒请求
 *
 * <p>当前 SDK 未生成该接口的 typed request，这里复用 SDK 的公共参数与签名逻辑，
 * 仅自定义 biz 请求体。</p>
 *
 * @author system
 * @since 2026-04-19
 */
@ApiMeta(
        path = "/common/developer/setBlockMsgList",
        businessId = 16,
        apiVersion = "10003",
        apiName = "common_developer_set_block_msg_list",
        needAuth = true
)
public class MtSetBlockMsgListRequest implements MeituanRequest<Object> {

    private static final Integer TARGET_BUSINESS_ID = 16;

    private final String accessToken;
    private final List<Integer> blockMsgList;

    public MtSetBlockMsgListRequest(String accessToken, List<Integer> blockMsgList) {
        this.accessToken = accessToken;
        this.blockMsgList = blockMsgList == null ? Collections.emptyList() : List.copyOf(blockMsgList);
    }

    @Override
    public MeituanResponse<Object> deserializeResponse(String response) {
        Type responseType = new TypeToken<MeituanResponse<Object>>() {
        }.getType();
        return com.meituan.sdk.internal.utils.JsonUtil.fromJson(response, responseType);
    }

    @Override
    public String serializeToJson() {
        return JsonUtil.toJson(new BizPayload(
                blockMsgList,
                accessToken,
                TARGET_BUSINESS_ID
        ));
    }

    private record BizPayload(List<Integer> blockMsgList,
                              String targetAppAuthToken,
                              Integer targetBusinessId) {
    }
}
