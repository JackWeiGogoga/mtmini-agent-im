package com.gogoga.backend.client;

import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.im.setAutoSendConfigInfo.ImTypeSwitchStatusParam;
import com.meituan.sdk.model.wmoperNg.im.setAutoSendConfigInfo.SetAutoSendConfigInfoRequest;
import com.meituan.sdk.model.wmoperNg.im.setPoiImStatus.SetPoiImStatusRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 美团门店 IM 配置客户端。仅封装两个固定写操作，不查询远端开关状态，也不在调用层重试。
 */
@Component
@RequiredArgsConstructor
public class MtImConfigurationClient {

    private static final int IM_STATUS_ENABLED = 1;
    private static final int ALL_SWITCH_TYPES = 0;
    private static final int SWITCH_STATUS_DISABLED = 0;

    private final MeituanClient meituanClient;

    public ImConfigurationResult enablePoiIm(String accessToken) {
        requireAccessToken(accessToken);
        SetPoiImStatusRequest request = new SetPoiImStatusRequest();
        request.setImStatus(IM_STATUS_ENABLED);
        return invoke(request, accessToken, "SET_POI_IM_STATUS");
    }

    public ImConfigurationResult disableAllSmartReplies(String accessToken) {
        requireAccessToken(accessToken);
        ImTypeSwitchStatusParam status = new ImTypeSwitchStatusParam();
        status.setType(ALL_SWITCH_TYPES);
        status.setSwitchStatus(SWITCH_STATUS_DISABLED);
        SetAutoSendConfigInfoRequest request = new SetAutoSendConfigInfoRequest();
        request.setAllStatus(List.of(status));
        return invoke(request, accessToken, "SET_AUTO_SEND_CONFIG");
    }

    private ImConfigurationResult invoke(com.meituan.sdk.MeituanRequest<String> request,
                                         String accessToken,
                                         String stage) {
        try {
            MeituanResponse<String> response = meituanClient.invokeApi(request, accessToken);
            if (response == null) {
                return ImConfigurationResult.failure(stage, "EMPTY_RESPONSE", null);
            }
            String summary = responseSummary(response);
            if (!response.isSuccess()) {
                return ImConfigurationResult.failure(
                        stage,
                        StringUtils.defaultIfBlank(response.getCode(), "UPSTREAM_FAILED"),
                        summary
                );
            }
            return ImConfigurationResult.success(stage, summary);
        } catch (MtSdkException e) {
            return ImConfigurationResult.failure(stage, "SDK_ERROR", null);
        }
    }

    private void requireAccessToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("MT_IM_ACCESS_TOKEN_MISSING");
        }
    }

    private String responseSummary(MeituanResponse<?> response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("code", response.getCode());
        summary.put("traceId", response.getTraceId());
        summary.put("dataPresent", response.getData() != null);
        return JsonUtil.toJson(summary);
    }

    public record ImConfigurationResult(boolean success, String stage, String errorCode, String responseSummary) {

        static ImConfigurationResult success(String stage, String responseSummary) {
            return new ImConfigurationResult(true, stage, null, responseSummary);
        }

        static ImConfigurationResult failure(String stage, String errorCode, String responseSummary) {
            return new ImConfigurationResult(false, stage, errorCode, responseSummary);
        }
    }
}
