package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信配置属性
 *
 * @author system
 * @since 2026-03-22
 */
@Data
@Component
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {

    /**
     * 是否启用 mock 短信发送
     */
    private boolean mockEnabled = true;

    /**
     * 验证码有效期（秒）
     */
    private long codeExpireSeconds = 300;

    /**
     * 发送间隔限制（秒）
     */
    private long sendIntervalSeconds = 60;

    /**
     * 每个手机号每日最多发送次数
     */
    private long dailySendLimit = 20;

    /**
     * 单个手机号验证码错误次数上限
     */
    private long verifyFailLimit = 5;

    /**
     * 单个 IP 每分钟最多发送次数
     */
    private long ipMinuteSendLimit = 10;

    /**
     * 单个 IP 每日最多发送次数
     */
    private long ipDailySendLimit = 100;

    /**
     * 腾讯云短信配置
     */
    private TencentConfig tencent = new TencentConfig();

    @Data
    public static class TencentConfig {

        /**
         * 腾讯云 SecretId
         */
        private String secretId;

        /**
         * 腾讯云 SecretKey
         */
        private String secretKey;

        /**
         * 短信 AppId
         */
        private String sdkAppId;

        /**
         * 短信签名
         */
        private String signName;

        /**
         * 登录验证码模板 ID
         */
        private String loginTemplateId;

        /**
         * 接入地域
         */
        private String region = "ap-guangzhou";

        /**
         * 接口地址
         */
        private String endpoint = "sms.tencentcloudapi.com";
    }
}
