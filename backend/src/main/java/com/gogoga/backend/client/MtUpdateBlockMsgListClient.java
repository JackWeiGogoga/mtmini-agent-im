package com.gogoga.backend.client;

import com.gogoga.backend.client.request.MtQueryBlockMsgListRequest;
import com.gogoga.backend.client.request.MtSetBlockMsgListRequest;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 美团开发者开放平台客户端
 * 用于承接开发者侧未生成 typed request 的接口调用
 *
 * @author system
 * @since 2026-03-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MtUpdateBlockMsgListClient {

    private static final int ORDER_PUSH_MSG_TYPE = 16102;

    private final MeituanClient meituanClient;

    /**
     * 关闭订单推送消息，并返回原始调用结果
     *
     * @param accessToken 授权 access token
     * @param opBizCode 商户标识
     * @return 调用结果
     */
    public UpdateBlockMsgListResult blockOrderPushResult(String accessToken, String opBizCode) {
        return updateBlockMsgListResult(accessToken, opBizCode, List.of(ORDER_PUSH_MSG_TYPE));
    }

    /**
     * 打开订单推送消息，并返回原始调用结果
     *
     * @param accessToken 授权 access token
     * @param opBizCode 商户标识
     * @return 调用结果
     */
    public UpdateBlockMsgListResult unblockOrderPushResult(String accessToken, String opBizCode) {
        return updateBlockMsgListResult(accessToken, opBizCode, Collections.emptyList());
    }

    /**
     * 设置消息屏蔽列表
     *
     * <p>例如：订单推送关闭传 `[16102]`，打开传 `[]`。</p>
     *
     * @param accessToken 授权 access token
     * @param opBizCode 商户标识
     * @param blockMsgList 需要屏蔽的消息类型列表
     * @return true 表示调用成功
     */
    public boolean updateBlockMsgList(String accessToken, String opBizCode, List<Integer> blockMsgList) {
        return updateBlockMsgListResult(accessToken, opBizCode, blockMsgList).success();
    }

    /**
     * 查询消息屏蔽列表
     *
     * @param accessToken 授权 access token，同时作为 targetAppAuthToken
     * @param opBizCode 商户标识
     * @return 当前屏蔽列表
     */
    public List<Integer> queryBlockMsgList(String accessToken, String opBizCode) {
        return queryBlockMsgListResult(accessToken, opBizCode).blockMsgList();
    }

    /**
     * 查询消息屏蔽列表，并返回原始调用结果
     *
     * @param accessToken 授权 access token，同时作为 targetAppAuthToken
     * @param opBizCode 商户标识
     * @return 查询结果
     */
    public QueryBlockMsgListResult queryBlockMsgListResult(String accessToken, String opBizCode) {
        if (StringUtils.isBlank(accessToken)) {
            return QueryBlockMsgListResult.failure(Collections.emptyList(), null, "缺少 accessToken", null, null);
        }

        try {
            MeituanResponse<MtQueryBlockMsgListRequest.QueryBlockMsgListData> response = meituanClient.invokeApi(
                    new MtQueryBlockMsgListRequest(accessToken),
                    accessToken
            );
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询美团消息屏蔽列表返回非成功状态，opBizCode={}, code={}, msg={}, traceId={}, response={}",
                        opBizCode,
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        response);
                return QueryBlockMsgListResult.failure(
                        Collections.emptyList(),
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        response
                );
            }

            List<Integer> blockMsgList = response.getData().getBlockMsgList() == null
                    ? Collections.emptyList()
                    : List.copyOf(response.getData().getBlockMsgList());
            return QueryBlockMsgListResult.success(blockMsgList, response);
        } catch (MtSdkException e) {
            log.error("查询美团消息屏蔽列表异常，opBizCode={}", opBizCode, e);
            return QueryBlockMsgListResult.failure(Collections.emptyList(), null, e.getMessage(), null, null);
        }
    }

    /**
     * 设置消息屏蔽列表，并返回原始调用结果
     *
     * @param accessToken 授权 access token
     * @param opBizCode 商户标识
     * @param blockMsgList 需要屏蔽的消息类型列表
     * @return 调用结果
     */
    public UpdateBlockMsgListResult updateBlockMsgListResult(String accessToken, String opBizCode, List<Integer> blockMsgList) {
        if (StringUtils.isBlank(accessToken)) {
            return UpdateBlockMsgListResult.failure(Collections.emptyList(), null, "缺少 accessToken", null, null);
        }

        List<Integer> safeBlockMsgList = blockMsgList == null ? Collections.emptyList() : List.copyOf(blockMsgList);
        try {
            MeituanResponse<Object> response = meituanClient.invokeApi(
                    new MtSetBlockMsgListRequest(accessToken, safeBlockMsgList),
                    accessToken
            );
            if (response == null || !response.isSuccess()) {
                log.warn("设置美团消息屏蔽列表返回非成功状态，opBizCode={}, blockMsgList={}, code={}, msg={}, traceId={}, response={}",
                        opBizCode,
                        safeBlockMsgList,
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        response);
                return UpdateBlockMsgListResult.failure(
                        safeBlockMsgList,
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        response
                );
            }
            return UpdateBlockMsgListResult.success(safeBlockMsgList, response);
        } catch (MtSdkException e) {
            log.error("设置美团消息屏蔽列表异常，opBizCode={}, blockMsgList={}", opBizCode, safeBlockMsgList, e);
            return UpdateBlockMsgListResult.failure(safeBlockMsgList, null, e.getMessage(), null, null);
        }
    }

    public record UpdateBlockMsgListResult(boolean success,
                                           List<Integer> blockMsgList,
                                           String code,
                                           String msg,
                                           String traceId,
                                           MeituanResponse<Object> response) {

        public static UpdateBlockMsgListResult success(List<Integer> blockMsgList, MeituanResponse<Object> response) {
            return new UpdateBlockMsgListResult(
                    true,
                    Objects.requireNonNullElse(blockMsgList, Collections.emptyList()),
                    response == null ? null : response.getCode(),
                    response == null ? null : response.getMsg(),
                    response == null ? null : response.getTraceId(),
                    response
            );
        }

        public static UpdateBlockMsgListResult failure(List<Integer> blockMsgList,
                                                       String code,
                                                       String msg,
                                                       String traceId,
                                                       MeituanResponse<Object> response) {
            return new UpdateBlockMsgListResult(
                    false,
                    Objects.requireNonNullElse(blockMsgList, Collections.emptyList()),
                    code,
                    msg,
                    traceId,
                    response
            );
        }
    }

    public record QueryBlockMsgListResult(boolean success,
                                          List<Integer> blockMsgList,
                                          String code,
                                          String msg,
                                          String traceId,
                                          MeituanResponse<MtQueryBlockMsgListRequest.QueryBlockMsgListData> response) {

        public static QueryBlockMsgListResult success(List<Integer> blockMsgList,
                                                      MeituanResponse<MtQueryBlockMsgListRequest.QueryBlockMsgListData> response) {
            return new QueryBlockMsgListResult(
                    true,
                    Objects.requireNonNullElse(blockMsgList, Collections.emptyList()),
                    response == null ? null : response.getCode(),
                    response == null ? null : response.getMsg(),
                    response == null ? null : response.getTraceId(),
                    response
            );
        }

        public static QueryBlockMsgListResult failure(List<Integer> blockMsgList,
                                                      String code,
                                                      String msg,
                                                      String traceId,
                                                      MeituanResponse<MtQueryBlockMsgListRequest.QueryBlockMsgListData> response) {
            return new QueryBlockMsgListResult(
                    false,
                    Objects.requireNonNullElse(blockMsgList, Collections.emptyList()),
                    code,
                    msg,
                    traceId,
                    response
            );
        }
    }
}
