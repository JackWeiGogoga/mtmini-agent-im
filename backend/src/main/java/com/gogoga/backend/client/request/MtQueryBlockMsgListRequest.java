package com.gogoga.backend.client.request;

import com.google.gson.reflect.TypeToken;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanRequest;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.annotations.ApiMeta;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 查询开发者绑定门店停止推送消息列表请求
 *
 * <p>当前 SDK 未生成该接口的 typed request，这里复用 SDK 的公共参数与签名逻辑，
 * 仅自定义 biz 请求体。</p>
 */
@ApiMeta(
        path = "/common/developer/queryBlockMsgList",
        businessId = 16,
        apiVersion = "10003",
        apiName = "common_developer_query_block_msg_list",
        needAuth = true
)
public class MtQueryBlockMsgListRequest implements MeituanRequest<MtQueryBlockMsgListRequest.QueryBlockMsgListData> {

    private static final Integer TARGET_BUSINESS_ID = 16;

    private final String targetAppAuthToken;

    public MtQueryBlockMsgListRequest(String targetAppAuthToken) {
        this.targetAppAuthToken = targetAppAuthToken;
    }

    @Override
    public MeituanResponse<QueryBlockMsgListData> deserializeResponse(String response) {
        Type responseType = new TypeToken<MeituanResponse<QueryBlockMsgListData>>() {
        }.getType();
        return com.meituan.sdk.internal.utils.JsonUtil.fromJson(response, responseType);
    }

    @Override
    public String serializeToJson() {
        return JsonUtil.toJson(new BizPayload(targetAppAuthToken, TARGET_BUSINESS_ID));
    }

    private record BizPayload(String targetAppAuthToken,
                              Integer targetBusinessId) {
    }

    public static class QueryBlockMsgListData {

        private List<Integer> blockMsgList;

        public List<Integer> getBlockMsgList() {
            return blockMsgList;
        }

        public void setBlockMsgList(List<Integer> blockMsgList) {
            this.blockMsgList = blockMsgList;
        }

        @Override
        public String toString() {
            return "QueryBlockMsgListData{" +
                    "blockMsgList=" + blockMsgList +
                    '}';
        }
    }
}
