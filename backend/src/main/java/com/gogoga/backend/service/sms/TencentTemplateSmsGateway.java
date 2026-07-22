package com.gogoga.backend.service.sms;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.util.PhoneUtil;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 腾讯云模板短信网关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentTemplateSmsGateway implements TemplateSmsGateway {

    private static final Set<String> RETRYABLE_STATUS_CODES = Set.of(
            "InternalError",
            "LimitExceeded.PhoneNumberDailyLimit",
            "LimitExceeded.PhoneNumberOneHourLimit",
            "LimitExceeded.PhoneNumberThirtySecondLimit",
            "LimitExceeded.PhoneNumberCountLimit"
    );

    private final SmsProperties smsProperties;

    @Override
    public TemplateSmsResult send(TemplateSmsRequest request) {
        validateRequest(request);
        SmsProperties.TencentConfig config = smsProperties.getTencent();
        validateConfig(config);

        try {
            SendSmsResponse response = createClient(config).SendSms(toTencentRequest(request, config));
            SendStatus status = firstStatus(response);
            String code = status.getCode();
            if (!"Ok".equalsIgnoreCase(code)) {
                boolean retryable = isRetryableProviderCode(code);
                log.warn("腾讯云模板短信拒绝, phone={}, templateId={}, code={}, retryable={}, requestId={}",
                        PhoneUtil.maskPhone(request.phone()), request.templateId(), code, retryable,
                        response.getRequestId());
                throw new TemplateSmsException(retryable, code, status.getMessage());
            }
            log.info("腾讯云模板短信已受理, phone={}, templateId={}, requestId={}, serialNo={}",
                    PhoneUtil.maskPhone(request.phone()), request.templateId(), response.getRequestId(),
                    status.getSerialNo());
            return new TemplateSmsResult(
                    response.getRequestId(),
                    status.getSerialNo(),
                    code,
                    status.getMessage()
            );
        } catch (TencentCloudSDKException e) {
            String code = e.getErrorCode();
            boolean retryable = isRetryableSdkError(code);
            log.warn("腾讯云模板短信调用失败, phone={}, templateId={}, code={}, retryable={}",
                    PhoneUtil.maskPhone(request.phone()), request.templateId(), code, retryable, e);
            throw new TemplateSmsException(retryable, code, "腾讯云短信调用失败", e);
        }
    }

    boolean isRetryableProviderCode(String code) {
        if (StringUtils.isBlank(code)) {
            return true;
        }
        return RETRYABLE_STATUS_CODES.contains(code)
                || code.startsWith("InternalError")
                || code.startsWith("RequestLimitExceeded")
                || code.startsWith("ResourceUnavailable");
    }

    boolean isRetryableSdkError(String code) {
        if (StringUtils.isBlank(code)) {
            return true;
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        return normalized.startsWith("internalerror")
                || normalized.startsWith("requestlimitexceeded")
                || normalized.startsWith("resourceunavailable")
                || normalized.startsWith("failedoperation.servicebusy")
                || normalized.startsWith("network");
    }

    private SmsClient createClient(SmsProperties.TencentConfig config) {
        Credential credential = new Credential(config.getSecretId(), config.getSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setReqMethod("POST");
        httpProfile.setConnTimeout(10);
        httpProfile.setReadTimeout(10);
        httpProfile.setWriteTimeout(10);
        httpProfile.setEndpoint(config.getEndpoint());

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setSignMethod("HmacSHA256");
        clientProfile.setHttpProfile(httpProfile);
        return new SmsClient(credential, config.getRegion(), clientProfile);
    }

    private SendSmsRequest toTencentRequest(TemplateSmsRequest request, SmsProperties.TencentConfig config) {
        SendSmsRequest smsRequest = new SendSmsRequest();
        smsRequest.setSmsSdkAppId(config.getSdkAppId());
        smsRequest.setSignName(config.getSignName());
        smsRequest.setTemplateId(request.templateId());
        smsRequest.setTemplateParamSet(request.templateParams().toArray(String[]::new));
        smsRequest.setPhoneNumberSet(new String[]{PhoneUtil.toE164CnPhone(request.phone())});
        smsRequest.setSessionContext(StringUtils.left(StringUtils.defaultString(request.sceneContext()), 64));
        return smsRequest;
    }

    private SendStatus firstStatus(SendSmsResponse response) {
        SendStatus[] statuses = response == null ? null : response.getSendStatusSet();
        if (statuses == null || statuses.length == 0) {
            throw new TemplateSmsException(true, "EMPTY_RESPONSE", "腾讯云短信未返回发送状态");
        }
        if (statuses[0] == null) {
            throw new TemplateSmsException(true, "EMPTY_RESPONSE", "腾讯云短信未返回发送状态");
        }
        return statuses[0];
    }

    private void validateRequest(TemplateSmsRequest request) {
        if (request == null || !PhoneUtil.isValidCnPhone(request.phone()) || StringUtils.isBlank(request.templateId())) {
            throw new TemplateSmsException(false, "INVALID_REQUEST", "短信手机号或模板 ID 无效");
        }
    }

    private void validateConfig(SmsProperties.TencentConfig config) {
        if (StringUtils.isAnyBlank(
                config.getSecretId(),
                config.getSecretKey(),
                config.getSdkAppId(),
                config.getSignName(),
                config.getRegion(),
                config.getEndpoint())) {
            throw new TemplateSmsException(false, "INVALID_CONFIG", "腾讯云短信配置不完整");
        }
    }
}
