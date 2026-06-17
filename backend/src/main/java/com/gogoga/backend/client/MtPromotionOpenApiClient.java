package com.gogoga.backend.client;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.client.result.PeerBidPriceOpenApiResult;
import com.gogoga.backend.client.result.PromotionBidOpenApiResult;
import com.gogoga.backend.client.result.PromotionPlanStatusOpenApiResult;
import com.gogoga.backend.service.MtWaimaiAdDecryptService;
import com.gogoga.backend.service.promotion.PromotionExecutionWindow;
import com.gogoga.backend.service.promotion.PromotionPlanStatusUpdateResult;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.MeituanAuthFailureUtil;
import com.google.gson.reflect.TypeToken;
import com.meituan.sdk.CommonRequest;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanBid.AdUpdatePlanBidRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanBid.AdUpdatePlanBidResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanStatus.AdUpdatePlanStatusRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanStatus.AdUpdatePlanStatusResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.PeerAdBidpriceRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.PeerAdBidpriceResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 美团推广开放平台客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MtPromotionOpenApiClient {

    public static final int CHANNEL_DIAN_JIN = 1;
    public static final int STATUS_OPEN = 0;
    public static final int STATUS_CLOSE = 1;
    private static final int MAX_BID_CENTS = 400;
    private static final String AD_STATUS_INFO_PATH = "/wmoper/ng/waimaiad/status";
    private static final int AD_STATUS_INFO_BUSINESS_ID = 16;
    private static final Type STATUS_INFO_RESPONSE_TYPE = new TypeToken<MeituanResponse<Map<String, Object>>>() {
    }.getType();

    private final MeituanClient meituanClient;
    private final MtWaimaiAdDecryptService mtWaimaiAdDecryptService;
    private final MtProperties mtProperties;

    @PostConstruct
    public void logWriteDryRunMode() {
        log.info("美团推广写接口 dry run 配置已加载，mt.promotion.write-dry-run={}", isWriteDryRun());
    }

    /**
     * 更新点金推广计划状态
     */
    public PromotionPlanStatusUpdateResult updatePlanStatus(
            MtPromotionAutoBidCandidate candidate,
            Integer status,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger
    ) {
        return updatePlanStatus(
                candidate == null ? null : candidate.getShopId(),
                candidate == null ? null : candidate.getPoiId(),
                candidate == null ? null : candidate.getAccessToken(),
                status,
                slotTime,
                executionWindow,
                trigger
        );
    }

    /**
     * 更新点金推广计划状态
     */
    public PromotionPlanStatusUpdateResult updatePlanStatus(
            Long shopId,
            String poiId,
            String accessToken,
            Integer status,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger
    ) {
        PromotionPlanStatusUpdateResult result = new PromotionPlanStatusUpdateResult();
        result.setChannel(CHANNEL_DIAN_JIN);
        result.setStatus(status);

        if (shopId == null) {
            result.setErrorMsg("更新推广计划状态缺少门店信息");
            result.setPayloadJson(buildPayload(shopId, poiId, status, slotTime, executionWindow, trigger, null, result.getErrorMsg()));
            return result;
        }
        if (StringUtils.isBlank(accessToken)) {
            result.setErrorMsg("更新推广计划状态缺少 accessToken");
            result.setPayloadJson(buildPayload(shopId, poiId, status, slotTime, executionWindow, trigger, null, result.getErrorMsg()));
            return result;
        }
        if (status == null || (status != STATUS_OPEN && status != STATUS_CLOSE)) {
            result.setErrorMsg("更新推广计划状态参数不正确");
            result.setPayloadJson(buildPayload(shopId, poiId, status, slotTime, executionWindow, trigger, null, result.getErrorMsg()));
            return result;
        }
        if (isWriteDryRun()) {
            result.setSuccess(true);
            result.setPayloadJson(buildPayload(shopId, poiId, status, slotTime, executionWindow, trigger, null, "DRY_RUN_NO_REMOTE_CALL"));
            log.info("推广计划状态写接口 dry run，shopId={}, poiId={}, status={}, slotTime={}, trigger={}, payload={}",
                    shopId, poiId, status, slotTime, trigger, result.getPayloadJson());
            return result;
        }

        try {
            AdUpdatePlanStatusRequest request = new AdUpdatePlanStatusRequest();
            request.setChannel(CHANNEL_DIAN_JIN);
            request.setStatus(status);
            MeituanResponse<AdUpdatePlanStatusResponse> response = meituanClient.invokeApi(request, accessToken);
            fillUpdateStatusResponseMeta(result, response);
            result.setPayloadJson(buildPayload(shopId, poiId, status, slotTime, executionWindow, trigger, response, null));
            if (response == null || !response.isSuccess() || response.getData() == null) {
                result.setErrorMsg(appendMeituanResponseMeta("更新美团推广计划状态失败", response));
                return result;
            }

            AdUpdatePlanStatusResponse payload = response.getData();
            if (!Boolean.TRUE.equals(payload.getSuccess())) {
                result.setResponseCode(StringUtils.defaultIfBlank(payload.getCode(), result.getResponseCode()));
                result.setResponseMsg(StringUtils.defaultIfBlank(payload.getMessage(), result.getResponseMsg()));
                result.setAuthFailure(result.isAuthFailure()
                        || MeituanAuthFailureUtil.isAuthFailure(payload.getCode(), payload.getMessage()));
                result.setErrorMsg(appendStatusUpdateBusinessError(
                        StringUtils.defaultIfBlank(payload.getMessage(), "更新美团推广计划状态业务失败"),
                        payload,
                        response
                ));
                result.setPayloadJson(buildPayload(shopId, poiId, status, slotTime, executionWindow, trigger, response, result.getErrorMsg()));
                return result;
            }

            result.setSuccess(true);
            return result;
        } catch (MtSdkException e) {
            log.error("更新美团推广计划状态异常，shopId={}, status={}, slotTime={}",
                    shopId, status, slotTime, e);
            result.setErrorMsg("更新美团推广计划状态异常");
            result.setPayloadJson(buildPayload(shopId, poiId, status, slotTime, executionWindow, trigger, null, e.getMessage()));
            return result;
        }
    }

    /**
     * 查询点金推广计划状态
     */
    public PromotionPlanStatusOpenApiResult queryPlanStatus(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger
    ) {
        PromotionPlanStatusOpenApiResult result = new PromotionPlanStatusOpenApiResult();
        result.setChannel(CHANNEL_DIAN_JIN);
        if (candidate == null || candidate.getShopId() == null) {
            result.setErrorMsg("查询推广计划状态缺少门店信息");
            result.setPayloadJson(buildStatusInfoPayload(candidate, slotTime, executionWindow, trigger, null, result, result.getErrorMsg()));
            return result;
        }
        if (StringUtils.isBlank(candidate.getAccessToken())) {
            result.setErrorMsg("查询推广计划状态缺少 accessToken");
            result.setPayloadJson(buildStatusInfoPayload(candidate, slotTime, executionWindow, trigger, null, result, result.getErrorMsg()));
            return result;
        }

        try {
            MeituanResponse<Map<String, Object>> response = meituanClient.commonInvokeApi(
                    buildStatusInfoCommonRequest(candidate.getAccessToken()),
                    STATUS_INFO_RESPONSE_TYPE
            );
            fillStatusResponseMeta(result, response);
            Map<String, Object> payload = response == null ? null : response.getData();
            if (response == null || !response.isSuccess() || payload == null) {
                result.setErrorMsg(appendMeituanResponseMsg("查询美团推广计划状态失败", response));
                result.setPayloadJson(buildStatusInfoPayload(candidate, slotTime, executionWindow, trigger, response, result, null));
                return result;
            }

            Map<String, Object> businessResult = asMap(payload.get("result"));
            if (businessResult == null || !Boolean.TRUE.equals(asBoolean(businessResult.get("success")))) {
                result.setErrorMsg(StringUtils.defaultIfBlank(
                        businessResult == null ? null : asString(businessResult.get("message")),
                        "查询美团推广计划状态业务失败"
                ));
                result.setAuthFailure(result.isAuthFailure()
                        || MeituanAuthFailureUtil.isAuthFailure(
                                businessResult == null ? null : asString(businessResult.get("code")),
                                businessResult == null ? null : asString(businessResult.get("message"))
                        ));
                result.setPayloadJson(buildStatusInfoPayload(candidate, slotTime, executionWindow, trigger, response, result, null));
                return result;
            }

            result.setSuccess(true);
            result.setStatus(asInteger(payload.get("status")));
            result.setBid(asInteger(payload.get("bid")));
            Long budget = asLong(payload.get("budget"));
            result.setBudget(budget == null ? null : centsToYuan(BigDecimal.valueOf(budget)));
            String budgetUsed = asString(payload.get("budgetUsed"));
            if (StringUtils.isNotBlank(budgetUsed)) {
                String decryptedBudgetUsed = mtWaimaiAdDecryptService.decryptValue(
                        candidate.getShopId(),
                        candidate.getAccessToken(),
                        budgetUsed,
                        "推广预算已用额度",
                        new HashMap<>()
                );
                result.setBudgetUsed(centsToYuan(new BigDecimal(decryptedBudgetUsed.trim())));
            }
            if (result.getBudget() != null && result.getBudgetUsed() != null) {
                result.setAvailableBudget(result.getBudget().subtract(result.getBudgetUsed()));
            }
            result.setPayloadJson(buildStatusInfoPayload(candidate, slotTime, executionWindow, trigger, response, result, null));
            return result;
        } catch (IllegalStateException e) {
            log.error("查询推广计划状态后解密预算异常，shopId={}, slotTime={}", candidate.getShopId(), slotTime, e);
            result.setErrorMsg(e.getMessage());
            result.setPayloadJson(buildStatusInfoPayload(candidate, slotTime, executionWindow, trigger, null, result, e.getMessage()));
            return result;
        } catch (MtSdkException e) {
            log.error("查询美团推广计划状态异常，shopId={}, slotTime={}", candidate.getShopId(), slotTime, e);
            result.setErrorMsg("查询美团推广计划状态异常");
            result.setPayloadJson(buildStatusInfoPayload(candidate, slotTime, executionWindow, trigger, null, result, e.getMessage()));
            return result;
        }
    }

    private CommonRequest buildStatusInfoCommonRequest(String accessToken) {
        CommonRequest request = new CommonRequest();
        request.setApiPath(AD_STATUS_INFO_PATH);
        request.setBusinessId(AD_STATUS_INFO_BUSINESS_ID);
        request.setAppAuthToken(accessToken);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("channel", String.valueOf(CHANNEL_DIAN_JIN));
        request.setBizParams(params);
        return request;
    }

    /**
     * 查询附近商家平均出价
     */
    public PeerBidPriceOpenApiResult queryPeerAverageBid(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger
    ) {
        PeerBidPriceOpenApiResult result = new PeerBidPriceOpenApiResult();
        result.setChannel(CHANNEL_DIAN_JIN);
        if (candidate == null || candidate.getShopId() == null) {
            result.setErrorMsg("查询附近商家平均出价缺少门店信息");
            result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, null, result, result.getErrorMsg()));
            logPeerBidFailure(candidate, slotTime, trigger, result, "LOCAL_VALIDATION");
            return result;
        }
        if (StringUtils.isBlank(candidate.getAccessToken())) {
            result.setErrorMsg("查询附近商家平均出价缺少 accessToken");
            result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, null, result, result.getErrorMsg()));
            logPeerBidFailure(candidate, slotTime, trigger, result, "LOCAL_VALIDATION");
            return result;
        }

        try {
            PeerAdBidpriceRequest request = new PeerAdBidpriceRequest();
            MeituanResponse<PeerAdBidpriceResponse> response = meituanClient.invokeApi(request, candidate.getAccessToken());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                result.setErrorMsg("查询附近商家平均出价失败");
                result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, response, result, null));
                logPeerBidFailure(candidate, slotTime, trigger, result, "GATEWAY_FAILURE");
                return result;
            }

            PeerAdBidpriceResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                result.setErrorMsg(StringUtils.defaultIfBlank(
                        payload.getResult() == null ? null : payload.getResult().getMessage(),
                        "查询附近商家平均出价业务失败"
                ));
                result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, response, result, null));
                logPeerBidFailure(candidate, slotTime, trigger, result, "BUSINESS_FAILURE");
                return result;
            }
            if (StringUtils.isBlank(payload.getAveragePrice())) {
                result.setErrorMsg("查询附近商家平均出价为空");
                result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, response, result, null));
                logPeerBidFailure(candidate, slotTime, trigger, result, "EMPTY_AVERAGE_PRICE");
                return result;
            }

            String decryptedAveragePrice = mtWaimaiAdDecryptService.decryptValue(
                    candidate.getShopId(),
                    candidate.getAccessToken(),
                    payload.getAveragePrice(),
                    "附近商家平均出价",
                    new HashMap<>()
            );

            result.setSuccess(true);
            result.setAverageBidAmount(centsToYuan(new BigDecimal(decryptedAveragePrice.trim())));
            result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, response, result, null));
            log.info("查询附近商家平均出价成功，shopId={}, poiId={}, slotTime={}, trigger={}, averageBidAmount={}",
                    candidate.getShopId(),
                    candidate.getPoiId(),
                    slotTime,
                    trigger,
                    result.getAverageBidAmount());
            return result;
        } catch (NumberFormatException e) {
            result.setErrorMsg("解析附近商家平均出价异常");
            result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, null, result, e.getMessage()));
            log.error("解析附近商家平均出价异常，shopId={}, poiId={}, slotTime={}, trigger={}, errorMsg={}, payload={}",
                    candidate.getShopId(),
                    candidate.getPoiId(),
                    slotTime,
                    trigger,
                    result.getErrorMsg(),
                    result.getPayloadJson(),
                    e);
            return result;
        } catch (MtSdkException e) {
            result.setErrorMsg("查询附近商家平均出价异常");
            result.setPayloadJson(buildPeerBidPayload(candidate, slotTime, executionWindow, trigger, null, result, e.getMessage()));
            log.error("查询附近商家平均出价异常，shopId={}, poiId={}, slotTime={}, trigger={}, errorMsg={}, payload={}",
                    candidate.getShopId(),
                    candidate.getPoiId(),
                    slotTime,
                    trigger,
                    result.getErrorMsg(),
                    result.getPayloadJson(),
                    e);
            return result;
        }
    }

    /**
     * 更新点金推广计划出价，单位分
     */
    public PromotionBidOpenApiResult updatePlanBid(
            MtPromotionAutoBidCandidate candidate,
            Integer bid,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger
    ) {
        PromotionBidOpenApiResult result = new PromotionBidOpenApiResult();
        result.setChannel(CHANNEL_DIAN_JIN);
        result.setBid(bid);

        if (candidate == null || candidate.getShopId() == null) {
            result.setErrorMsg("更新推广计划出价缺少门店信息");
            result.setPayloadJson(buildBidPayload(candidate, bid, slotTime, executionWindow, trigger, null, result.getErrorMsg()));
            return result;
        }
        if (StringUtils.isBlank(candidate.getAccessToken())) {
            result.setErrorMsg("更新推广计划出价缺少 accessToken");
            result.setPayloadJson(buildBidPayload(candidate, bid, slotTime, executionWindow, trigger, null, result.getErrorMsg()));
            return result;
        }
        if (bid == null || bid <= 0) {
            result.setErrorMsg("更新推广计划出价参数不正确");
            result.setPayloadJson(buildBidPayload(candidate, bid, slotTime, executionWindow, trigger, null, result.getErrorMsg()));
            return result;
        }
        if (bid > MAX_BID_CENTS) {
            result.setErrorMsg("更新推广计划出价不能超过4元");
            result.setPayloadJson(buildBidPayload(candidate, MAX_BID_CENTS, slotTime, executionWindow, trigger, null, result.getErrorMsg()));
            result.setBid(MAX_BID_CENTS);
            return result;
        }
        if (isWriteDryRun()) {
            result.setSuccess(true);
            result.setPayloadJson(buildBidPayload(candidate, bid, slotTime, executionWindow, trigger, null, "DRY_RUN_NO_REMOTE_CALL"));
            log.info("推广计划出价写接口 dry run，shopId={}, poiId={}, bid={}, slotTime={}, trigger={}, payload={}",
                    candidate.getShopId(), candidate.getPoiId(), bid, slotTime, trigger, result.getPayloadJson());
            return result;
        }

        try {
            AdUpdatePlanBidRequest request = new AdUpdatePlanBidRequest();
            request.setChannel(CHANNEL_DIAN_JIN);
            request.setBid(bid);
            MeituanResponse<AdUpdatePlanBidResponse> response = meituanClient.invokeApi(request, candidate.getAccessToken());
            result.setPayloadJson(buildBidPayload(candidate, bid, slotTime, executionWindow, trigger, response, null));
            if (response == null || !response.isSuccess() || response.getData() == null) {
                result.setErrorMsg(appendMeituanResponseMsg("更新美团推广计划出价失败", response));
                return result;
            }

            AdUpdatePlanBidResponse payload = response.getData();
            if (!Boolean.TRUE.equals(payload.getSuccess())) {
                result.setErrorMsg(appendMeituanResponseMsg(
                        StringUtils.defaultIfBlank(payload.getCode(), "更新美团推广计划出价业务失败"),
                        response
                ));
                return result;
            }

            result.setSuccess(true);
            return result;
        } catch (MtSdkException e) {
            log.error("更新美团推广计划出价异常，shopId={}, bid={}, slotTime={}",
                    candidate.getShopId(), bid, slotTime, e);
            result.setErrorMsg("更新美团推广计划出价异常");
            result.setPayloadJson(buildBidPayload(candidate, bid, slotTime, executionWindow, trigger, null, e.getMessage()));
            return result;
        }
    }

    private String appendMeituanResponseMsg(String localError, MeituanResponse<?> response) {
        String errorMsg = StringUtils.defaultIfBlank(localError, "美团接口调用失败");
        if (response == null || StringUtils.isBlank(response.getMsg())) {
            return errorMsg;
        }
        if (StringUtils.contains(errorMsg, response.getMsg())) {
            return errorMsg;
        }
        return errorMsg + "：" + response.getMsg();
    }

    private String appendMeituanResponseMeta(String localError, MeituanResponse<?> response) {
        String errorMsg = appendMeituanResponseMsg(localError, response);
        if (response == null) {
            return errorMsg;
        }
        StringBuilder detail = new StringBuilder();
        if (StringUtils.isNotBlank(response.getCode())) {
            detail.append("code=").append(response.getCode());
        }
        if (StringUtils.isNotBlank(response.getTraceId())) {
            if (!detail.isEmpty()) {
                detail.append(", ");
            }
            detail.append("traceId=").append(response.getTraceId());
        }
        if (detail.isEmpty()) {
            return errorMsg;
        }
        return errorMsg + "（" + detail + "）";
    }

    private String appendStatusUpdateBusinessError(
            String localError,
            AdUpdatePlanStatusResponse payload,
            MeituanResponse<?> response
    ) {
        String errorMsg = StringUtils.defaultIfBlank(localError, "更新美团推广计划状态业务失败");
        StringBuilder detail = new StringBuilder();
        if (payload != null && StringUtils.isNotBlank(payload.getCode())) {
            detail.append("code=").append(payload.getCode());
        } else if (response != null && StringUtils.isNotBlank(response.getCode())) {
            detail.append("code=").append(response.getCode());
        }
        if (response != null && StringUtils.isNotBlank(response.getTraceId())) {
            if (!detail.isEmpty()) {
                detail.append(", ");
            }
            detail.append("traceId=").append(response.getTraceId());
        }
        if (detail.isEmpty()) {
            return errorMsg;
        }
        return errorMsg + "（" + detail + "）";
    }

    private String buildPayload(
            Long shopId,
            String poiId,
            Integer status,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger,
            MeituanResponse<AdUpdatePlanStatusResponse> response,
            String localError
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shopId", shopId);
        payload.put("poiId", poiId);
        payload.put("slotTime", slotTime == null ? null : slotTime.toString());
        payload.put("windowStartAt", executionWindow == null || executionWindow.getWindowStartAt() == null
                ? null : executionWindow.getWindowStartAt().toString());
        payload.put("windowEndAt", executionWindow == null || executionWindow.getWindowEndAt() == null
                ? null : executionWindow.getWindowEndAt().toString());
        payload.put("windowKey", executionWindow == null ? null : executionWindow.getWindowKey());
        payload.put("channel", CHANNEL_DIAN_JIN);
        payload.put("status", status);
        payload.put("trigger", trigger);
        payload.put("dryRun", isWriteDryRun());
        payload.put("response", response == null ? null : response.toString());
        payload.put("responseCode", response == null ? null : response.getCode());
        payload.put("responseMsg", response == null ? null : response.getMsg());
        payload.put("responseTraceId", response == null ? null : response.getTraceId());
        payload.put("authFailure", isUpdateStatusAuthFailure(response));
        payload.put("localError", localError);
        return JsonUtil.toJson(payload);
    }

    private String buildBidPayload(
            MtPromotionAutoBidCandidate candidate,
            Integer bid,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger,
            MeituanResponse<AdUpdatePlanBidResponse> response,
            String localError
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shopId", candidate == null ? null : candidate.getShopId());
        payload.put("poiId", candidate == null ? null : candidate.getPoiId());
        payload.put("slotTime", slotTime == null ? null : slotTime.toString());
        payload.put("windowStartAt", executionWindow == null || executionWindow.getWindowStartAt() == null
                ? null : executionWindow.getWindowStartAt().toString());
        payload.put("windowEndAt", executionWindow == null || executionWindow.getWindowEndAt() == null
                ? null : executionWindow.getWindowEndAt().toString());
        payload.put("windowKey", executionWindow == null ? null : executionWindow.getWindowKey());
        payload.put("channel", CHANNEL_DIAN_JIN);
        payload.put("bid", bid);
        payload.put("trigger", trigger);
        payload.put("dryRun", isWriteDryRun());
        payload.put("response", response == null ? null : response.toString());
        payload.put("responseCode", response == null ? null : response.getCode());
        payload.put("responseMsg", response == null ? null : response.getMsg());
        payload.put("responseTraceId", response == null ? null : response.getTraceId());
        payload.put("localError", localError);
        return JsonUtil.toJson(payload);
    }

    private String buildStatusInfoPayload(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger,
            MeituanResponse<?> response,
            PromotionPlanStatusOpenApiResult result,
            String localError
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shopId", candidate == null ? null : candidate.getShopId());
        payload.put("poiId", candidate == null ? null : candidate.getPoiId());
        payload.put("slotTime", slotTime == null ? null : slotTime.toString());
        payload.put("windowStartAt", executionWindow == null || executionWindow.getWindowStartAt() == null
                ? null : executionWindow.getWindowStartAt().toString());
        payload.put("windowEndAt", executionWindow == null || executionWindow.getWindowEndAt() == null
                ? null : executionWindow.getWindowEndAt().toString());
        payload.put("windowKey", executionWindow == null ? null : executionWindow.getWindowKey());
        payload.put("channel", CHANNEL_DIAN_JIN);
        payload.put("status", result == null ? null : result.getStatus());
        payload.put("bid", result == null ? null : result.getBid());
        payload.put("budget", result == null ? null : result.getBudget());
        payload.put("budgetUsed", result == null ? null : result.getBudgetUsed());
        payload.put("availableBudget", result == null ? null : result.getAvailableBudget());
        payload.put("trigger", trigger);
        payload.put("responseCode", response == null ? null : response.getCode());
        payload.put("responseMsg", response == null ? null : response.getMsg());
        payload.put("responseTraceId", response == null ? null : response.getTraceId());
        payload.put("responseData", response == null ? null : JsonUtil.objectToMap(response.getData()));
        payload.put("authFailure", result != null && result.isAuthFailure());
        payload.put("localError", localError);
        return JsonUtil.toJson(payload);
    }

    private void fillStatusResponseMeta(PromotionPlanStatusOpenApiResult result, MeituanResponse<?> response) {
        if (result == null || response == null) {
            return;
        }
        result.setResponseCode(response.getCode());
        result.setResponseMsg(response.getMsg());
        result.setResponseTraceId(response.getTraceId());
        result.setAuthFailure(isAuthFailureResponse(response));
    }

    private void fillUpdateStatusResponseMeta(PromotionPlanStatusUpdateResult result, MeituanResponse<?> response) {
        if (result == null || response == null) {
            return;
        }
        result.setResponseCode(response.getCode());
        result.setResponseMsg(response.getMsg());
        result.setResponseTraceId(response.getTraceId());
        result.setAuthFailure(isAuthFailureResponse(response));
    }

    private boolean isAuthFailureResponse(MeituanResponse<?> response) {
        return response != null
                && MeituanAuthFailureUtil.isAuthFailure(response.getCode(), response.getMsg());
    }

    private boolean isUpdateStatusAuthFailure(MeituanResponse<AdUpdatePlanStatusResponse> response) {
        if (isAuthFailureResponse(response)) {
            return true;
        }
        AdUpdatePlanStatusResponse payload = response == null ? null : response.getData();
        return payload != null && MeituanAuthFailureUtil.isAuthFailure(payload.getCode(), payload.getMessage());
    }

    private String buildPeerBidPayload(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            PromotionExecutionWindow executionWindow,
            String trigger,
            MeituanResponse<PeerAdBidpriceResponse> response,
            PeerBidPriceOpenApiResult result,
            String localError
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shopId", candidate == null ? null : candidate.getShopId());
        payload.put("poiId", candidate == null ? null : candidate.getPoiId());
        payload.put("slotTime", slotTime == null ? null : slotTime.toString());
        payload.put("windowStartAt", executionWindow == null || executionWindow.getWindowStartAt() == null
                ? null : executionWindow.getWindowStartAt().toString());
        payload.put("windowEndAt", executionWindow == null || executionWindow.getWindowEndAt() == null
                ? null : executionWindow.getWindowEndAt().toString());
        payload.put("windowKey", executionWindow == null ? null : executionWindow.getWindowKey());
        payload.put("channel", CHANNEL_DIAN_JIN);
        payload.put("averageBidAmount", result == null ? null : result.getAverageBidAmount());
        payload.put("trigger", trigger);
        payload.put("response", response == null ? null : response.toString());
        payload.put("localError", localError);
        return JsonUtil.toJson(payload);
    }

    private void logPeerBidFailure(
            MtPromotionAutoBidCandidate candidate,
            LocalDateTime slotTime,
            String trigger,
            PeerBidPriceOpenApiResult result,
            String phase
    ) {
        log.warn("查询附近商家平均出价未命中有效结果，phase={}, shopId={}, poiId={}, slotTime={}, trigger={}, errorMsg={}, payload={}",
                phase,
                candidate == null ? null : candidate.getShopId(),
                candidate == null ? null : candidate.getPoiId(),
                slotTime,
                trigger,
                result == null ? null : result.getErrorMsg(),
                result == null ? null : result.getPayloadJson());
    }

    private BigDecimal centsToYuan(BigDecimal cents) {
        if (cents == null) {
            return null;
        }
        return cents.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        Long longValue = asLong(value);
        return longValue == null ? null : longValue.intValue();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isWriteDryRun() {
        return mtProperties != null
                && mtProperties.getPromotion() != null
                && mtProperties.getPromotion().isWriteDryRun();
    }
}
