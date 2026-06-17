package com.gogoga.backend.service;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.service.sms.MockSmsSender;
import com.gogoga.backend.service.sms.SmsSender;
import com.gogoga.backend.service.sms.TencentSmsSender;
import com.gogoga.backend.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码登录服务
 *
 * @author system
 * @since 2026-03-22
 */
@Service
@RequiredArgsConstructor
public class SmsLoginService {

    private static final String SMS_CODE_KEY_PREFIX = "auth:sms:code:";
    private static final String SMS_COOLDOWN_KEY_PREFIX = "auth:sms:cooldown:";
    private static final String SMS_DAILY_KEY_PREFIX = "auth:sms:daily:";
    private static final String SMS_VERIFY_FAIL_KEY_PREFIX = "auth:sms:verify-fail:";
    private static final String SMS_IP_MINUTE_KEY_PREFIX = "auth:sms:ip:minute:";
    private static final String SMS_IP_DAILY_KEY_PREFIX = "auth:sms:ip:daily:";

    private final SmsProperties smsProperties;
    private final StringRedisTemplate redisTemplate;
    private final MockSmsSender mockSmsSender;
    private final TencentSmsSender tencentSmsSender;

    /**
     * 发送登录验证码
     *
     * @param rawPhone 原始手机号
     * @return 归一化后的手机号
     */
    public String sendLoginCode(String rawPhone, String clientIp) {
        String phone = normalizeAndValidatePhone(rawPhone);
        checkSendLimit(phone, clientIp);

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        long codeExpireSeconds = smsProperties.getCodeExpireSeconds();
        long sendIntervalSeconds = smsProperties.getSendIntervalSeconds();

        redisTemplate.opsForValue().set(getCodeKey(phone), code, codeExpireSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(getCooldownKey(phone), "1", sendIntervalSeconds, TimeUnit.SECONDS);
        redisTemplate.delete(getVerifyFailKey(phone));

        try {
            Long dailyCount = redisTemplate.opsForValue().increment(getDailyKey(phone));
            if (dailyCount != null && dailyCount == 1L) {
                redisTemplate.expire(getDailyKey(phone), Duration.ofDays(1));
            }

            incrementIpCounters(clientIp);

            getSmsSender().sendLoginCode(phone, code, Math.max(1L, codeExpireSeconds / 60));
        } catch (RuntimeException e) {
            redisTemplate.delete(getCodeKey(phone));
            redisTemplate.delete(getCooldownKey(phone));
            throw e;
        }
        return phone;
    }

    /**
     * 校验登录验证码
     *
     * @param rawPhone 原始手机号
     * @param code 验证码
     * @return 归一化后的手机号
     */
    public String verifyLoginCode(String rawPhone, String code) {
        String phone = normalizeAndValidatePhone(rawPhone);
        if (StringUtils.isBlank(code)) {
            throw new AuthenticationException(AuthenticationException.INVALID_SMS_CODE, "验证码不能为空");
        }

        checkVerifyFailLimit(phone);

        String cachedCode = redisTemplate.opsForValue().get(getCodeKey(phone));
        if (cachedCode == null) {
            redisTemplate.delete(getVerifyFailKey(phone));
            throw new AuthenticationException(AuthenticationException.SMS_CODE_EXPIRED, "验证码已过期，请重新获取");
        }

        if (!Objects.equals(cachedCode, code.trim())) {
            incrementVerifyFailCount(phone);
            throw new AuthenticationException(AuthenticationException.INVALID_SMS_CODE, "验证码错误");
        }

        redisTemplate.delete(getVerifyFailKey(phone));
        return phone;
    }

    /**
     * 归一化并校验手机号
     *
     * @param rawPhone 原始手机号
     * @return 归一化后的手机号
     */
    public String normalizeAndValidatePhone(String rawPhone) {
        String phone = PhoneUtil.normalizeCnPhone(rawPhone);
        if (!PhoneUtil.isValidCnPhone(phone)) {
            throw new AuthenticationException(AuthenticationException.INVALID_PHONE, "请输入有效的手机号");
        }
        return phone;
    }

    private void checkSendLimit(String phone, String clientIp) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(getCooldownKey(phone)))) {
            throw new AuthenticationException(AuthenticationException.SMS_SEND_TOO_FREQUENT,
                    "验证码发送过于频繁，请稍后再试");
        }

        String dailyCount = redisTemplate.opsForValue().get(getDailyKey(phone));
        if (dailyCount != null && Long.parseLong(dailyCount) >= smsProperties.getDailySendLimit()) {
            throw new AuthenticationException(AuthenticationException.SMS_SEND_LIMIT_EXCEEDED,
                    "今日验证码发送次数已达上限，请明天再试");
        }

        checkIpSendLimit(clientIp);
    }

    private SmsSender getSmsSender() {
        return smsProperties.isMockEnabled() ? mockSmsSender : tencentSmsSender;
    }

    private String getCodeKey(String phone) {
        return SMS_CODE_KEY_PREFIX + phone;
    }

    private String getCooldownKey(String phone) {
        return SMS_COOLDOWN_KEY_PREFIX + phone;
    }

    private String getDailyKey(String phone) {
        return SMS_DAILY_KEY_PREFIX + phone;
    }

    private void checkVerifyFailLimit(String phone) {
        String verifyFailCount = redisTemplate.opsForValue().get(getVerifyFailKey(phone));
        if (verifyFailCount != null
                && Long.parseLong(verifyFailCount) >= smsProperties.getVerifyFailLimit()) {
            redisTemplate.delete(getCodeKey(phone));
            redisTemplate.delete(getCooldownKey(phone));
            redisTemplate.delete(getVerifyFailKey(phone));
            throw new AuthenticationException(AuthenticationException.SMS_LOGIN_RETRY_LIMIT_EXCEEDED,
                    "验证码错误次数过多，请重新获取验证码");
        }
    }

    private void incrementVerifyFailCount(String phone) {
        Long failCount = redisTemplate.opsForValue().increment(getVerifyFailKey(phone));
        if (failCount != null && failCount == 1L) {
            redisTemplate.expire(getVerifyFailKey(phone),
                    Duration.ofSeconds(smsProperties.getCodeExpireSeconds()));
        }
    }

    private void checkIpSendLimit(String clientIp) {
        if (StringUtils.isBlank(clientIp)) {
            return;
        }

        String minuteCount = redisTemplate.opsForValue().get(getIpMinuteKey(clientIp));
        if (minuteCount != null
                && Long.parseLong(minuteCount) >= smsProperties.getIpMinuteSendLimit()) {
            throw new AuthenticationException(AuthenticationException.SMS_IP_RATE_LIMIT_EXCEEDED,
                    "当前请求过于频繁，请稍后再试");
        }

        String dailyCount = redisTemplate.opsForValue().get(getIpDailyKey(clientIp));
        if (dailyCount != null
                && Long.parseLong(dailyCount) >= smsProperties.getIpDailySendLimit()) {
            throw new AuthenticationException(AuthenticationException.SMS_IP_RATE_LIMIT_EXCEEDED,
                    "当前请求过于频繁，请明天再试");
        }
    }

    private void incrementIpCounters(String clientIp) {
        if (StringUtils.isBlank(clientIp)) {
            return;
        }

        Long minuteCount = redisTemplate.opsForValue().increment(getIpMinuteKey(clientIp));
        if (minuteCount != null && minuteCount == 1L) {
            redisTemplate.expire(getIpMinuteKey(clientIp), Duration.ofMinutes(1));
        }

        Long dailyCount = redisTemplate.opsForValue().increment(getIpDailyKey(clientIp));
        if (dailyCount != null && dailyCount == 1L) {
            redisTemplate.expire(getIpDailyKey(clientIp), Duration.ofDays(1));
        }
    }

    private String getVerifyFailKey(String phone) {
        return SMS_VERIFY_FAIL_KEY_PREFIX + phone;
    }

    private String getIpMinuteKey(String clientIp) {
        return SMS_IP_MINUTE_KEY_PREFIX + clientIp;
    }

    private String getIpDailyKey(String clientIp) {
        return SMS_IP_DAILY_KEY_PREFIX + clientIp;
    }
}
