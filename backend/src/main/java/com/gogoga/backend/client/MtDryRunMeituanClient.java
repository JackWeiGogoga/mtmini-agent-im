package com.gogoga.backend.client;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.service.async.MtApiDryRunException;
import com.meituan.sdk.CommonRequest;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanRequest;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.auth.MeituanTokenResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.im.sendImMsgWmoper.SendImMsgWmoperRequest;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 美团 SDK 的全局 dry-run 安全代理。
 *
 * <p>所有 SDK 出站方法必须经过此处。日志只记录方法和请求类型，禁止记录 token、
 * OAuth code、请求内容、消息正文或密文。</p>
 */
@Slf4j
public class MtDryRunMeituanClient implements MeituanClient {

    private static final long DEFAULT_DRY_RUN_RESCHEDULE_SECONDS = 3600L;

    private final MeituanClient delegate;
    private final MtProperties mtProperties;

    public MtDryRunMeituanClient(MeituanClient delegate, MtProperties mtProperties) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.mtProperties = Objects.requireNonNull(mtProperties, "mtProperties");
    }

    @Override
    public <T> MeituanResponse<T> invokeApi(MeituanRequest<T> request) throws MtSdkException {
        checkDryRun("invokeApi", request);
        return delegate.invokeApi(request);
    }

    @Override
    public <T> MeituanResponse<T> invokeApi(MeituanRequest<T> request, String accessToken) throws MtSdkException {
        checkDryRun("invokeApiWithToken", request);
        return delegate.invokeApi(request, accessToken);
    }

    @Override
    public <T> MeituanResponse<T> invokeApi(MeituanRequest<T> request, Integer offset, Integer limit)
            throws MtSdkException {
        checkDryRun("invokeApiWithPage", request);
        return delegate.invokeApi(request, offset, limit);
    }

    @Override
    public <T> MeituanResponse<T> invokeApi(
            MeituanRequest<T> request,
            String accessToken,
            Integer offset,
            Integer limit
    ) throws MtSdkException {
        checkDryRun("invokeApiWithTokenAndPage", request);
        return delegate.invokeApi(request, accessToken, offset, limit);
    }

    @Override
    public <T> MeituanResponse<T> commonInvokeApi(CommonRequest request, Type responseType) throws MtSdkException {
        checkDryRun("commonInvokeApi", request);
        return delegate.commonInvokeApi(request, responseType);
    }

    @Override
    public MeituanTokenResponse getOAuthToken(Integer businessId, String code) throws MtSdkException {
        checkDryRun("getOAuthToken", null);
        return delegate.getOAuthToken(businessId, code);
    }

    @Override
    public MeituanTokenResponse refreshToken(Integer businessId, String refreshToken) throws MtSdkException {
        checkDryRun("refreshToken", null);
        return delegate.refreshToken(businessId, refreshToken);
    }

    private void checkDryRun(String method, Object request) {
        MtProperties.Api api = mtProperties.getApi();
        String reason = blockReason(api, request);
        if (reason == null) {
            return;
        }
        String requestType = request == null ? "NONE" : request.getClass().getSimpleName();
        String operation = method + ":" + requestType;
        log.warn("美团 SDK 调用被 dry run 阻断，method={}, requestType={}, reason={}", method, requestType, reason);
        long rescheduleSeconds = api == null
                ? DEFAULT_DRY_RUN_RESCHEDULE_SECONDS
                : api.getDryRunRescheduleSeconds();
        throw new MtApiDryRunException(operation, rescheduleSeconds);
    }

    /**
     * 返回阻断原因；返回 {@code null} 表示放行。IM 发送开关只在总开关之上额外收紧。
     */
    private String blockReason(MtProperties.Api api, Object request) {
        if (api == null || api.isDryRun()) {
            return "GLOBAL_DRY_RUN";
        }
        if (api.isImSendDryRun() && request instanceof SendImMsgWmoperRequest) {
            return "IM_SEND_DRY_RUN";
        }
        return null;
    }
}
