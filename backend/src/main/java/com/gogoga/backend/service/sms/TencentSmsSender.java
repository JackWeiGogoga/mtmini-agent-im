package com.gogoga.backend.service.sms;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.exception.AuthenticationException;
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

/**
 * 腾讯云短信发送器
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentSmsSender implements SmsSender {

    private final SmsProperties smsProperties;

    @Override
    public void sendLoginCode(String phone, String code, long expireMinutes) {
        SmsProperties.TencentConfig config = smsProperties.getTencent();
        validateConfig(config);

        try {
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

            SmsClient client = new SmsClient(credential, config.getRegion(), clientProfile);

            SendSmsRequest request = new SendSmsRequest();
            request.setSmsSdkAppId(config.getSdkAppId());
            request.setSignName(config.getSignName());
            request.setTemplateId(config.getLoginTemplateId());
            request.setTemplateParamSet(new String[]{code, String.valueOf(expireMinutes)});
            request.setPhoneNumberSet(new String[]{PhoneUtil.toE164CnPhone(phone)});
            request.setSessionContext("login");

            SendSmsResponse response = client.SendSms(request);
            validateSendStatus(response, phone);
            log.info("腾讯云短信发送成功, phone={}, requestId={}, response={}",
                    phone, response.getRequestId(), SendSmsResponse.toJsonString(response));
        } catch (TencentCloudSDKException e) {
            log.error("腾讯云短信发送失败, phone={}", phone, e);
            throw new AuthenticationException(AuthenticationException.SMS_SEND_ERROR,
                    "短信发送失败，请稍后重试", e);
        }
    }

    private void validateConfig(SmsProperties.TencentConfig config) {
        if (StringUtils.isAnyBlank(
                config.getSecretId(),
                config.getSecretKey(),
                config.getSdkAppId(),
                config.getSignName(),
                config.getLoginTemplateId(),
                config.getRegion(),
                config.getEndpoint())) {
            throw new AuthenticationException(AuthenticationException.SMS_SEND_ERROR,
                    "腾讯云短信配置不完整，请检查 sms.tencent 配置项");
        }
    }

    private void validateSendStatus(SendSmsResponse response, String phone) {
        SendStatus[] sendStatusSet = response.getSendStatusSet();
        if (sendStatusSet == null || sendStatusSet.length == 0) {
            throw new AuthenticationException(AuthenticationException.SMS_SEND_ERROR,
                    "短信发送失败，请稍后重试");
        }

        for (SendStatus sendStatus : sendStatusSet) {
            if (!"Ok".equalsIgnoreCase(sendStatus.getCode())) {
                log.warn("腾讯云短信发送状态异常, phone={}, code={}, message={}, serialNo={}",
                        phone,
                        sendStatus.getCode(),
                        sendStatus.getMessage(),
                        sendStatus.getSerialNo());
                throw new AuthenticationException(AuthenticationException.SMS_SEND_ERROR,
                        "短信发送失败，请稍后重试");
            }
        }
    }
}
