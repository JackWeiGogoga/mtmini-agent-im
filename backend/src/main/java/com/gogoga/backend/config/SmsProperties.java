package com.gogoga.backend.config;

import com.gogoga.backend.enums.BusinessSmsDeliveryModeEnum;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

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
     * 登录验证码是否启用 mock 发送；业务短信使用独立的 deliveryMode
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

    /**
     * 商家生命周期业务短信配置
     */
    private BusinessConfig business = new BusinessConfig();

    @PostConstruct
    public void validateBusinessConfig() {
        if (!business.isEnabled()) {
            return;
        }
        if (StringUtils.isAnyBlank(
                business.getApplicationName(),
                business.getPurchaseGuideTemplateId(),
                business.getPowerExhaustedTemplateId(),
                business.getAppExpiredTemplateId())) {
            throw new IllegalStateException("业务短信已启用，但应用名称或模板 ID 配置不完整");
        }
        if (business.getDeliveryMode() == null) {
            throw new IllegalStateException("业务短信投递模式不能为空");
        }
        if (business.getDeliveryMode() == BusinessSmsDeliveryModeEnum.REAL && (StringUtils.isAnyBlank(
                tencent.getSecretId(), tencent.getSecretKey(), tencent.getSdkAppId(), tencent.getSignName(),
                tencent.getRegion(), tencent.getEndpoint()) || containsPlaceholderTencentConfig())) {
            throw new IllegalStateException("业务短信已启用，但腾讯云凭证或签名配置不完整");
        }
        if (business.getPollSize() <= 0
                || business.getPlannerBatchSize() <= 0
                || business.getMaxRetryCount() < 0
                || business.getTimeoutSeconds() <= 0
                || business.getInitialBackoffSeconds() <= 0
                || business.getMaxBackoffSeconds() <= 0
                || business.getPollDelayMs() <= 0
                || business.getRecoveryDelayMs() <= 0) {
            throw new IllegalStateException("业务短信批次、超时、重试或轮询配置无效");
        }
        try {
            ZoneId.of(business.getZoneId());
            CronExpression.parse(business.getExpiryPlannerCron());
        } catch (RuntimeException e) {
            throw new IllegalStateException("业务短信时区或到期规划 cron 配置无效", e);
        }
    }

    private boolean containsPlaceholderTencentConfig() {
        return StringUtils.equalsAnyIgnoreCase(tencent.getSecretId(), "your-secret-id")
                || StringUtils.equalsAnyIgnoreCase(tencent.getSecretKey(), "your-secret-key")
                || StringUtils.equalsAnyIgnoreCase(tencent.getSdkAppId(), "1400000000")
                || StringUtils.equalsAnyIgnoreCase(tencent.getSignName(), "your-sign-name");
    }

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

    @Data
    public static class BusinessConfig {

        /** 是否创建和处理业务短信事件 */
        private boolean enabled = false;

        /** 是否启用事件和投递工作器 */
        private boolean workerEnabled = true;

        /** 是否启用应用到期计划任务 */
        private boolean expiryPlannerEnabled = true;

        /** 业务短信最终投递模式，默认只记录日志 */
        private BusinessSmsDeliveryModeEnum deliveryMode = BusinessSmsDeliveryModeEnum.LOG_ONLY;

        /** 短信中的固定应用名称 */
        private String applicationName = "AI智能体消息管理";

        /** 购买应用后引导客服模板 ID */
        private String purchaseGuideTemplateId = "2685915";

        /** 业务算力耗尽模板 ID */
        private String powerExhaustedTemplateId = "2687571";

        /** 应用到期模板 ID */
        private String appExpiredTemplateId = "2687569";

        /** 业务时间计算时区 */
        private String zoneId = "Asia/Shanghai";

        /** 单次事件或投递轮询数量 */
        private int pollSize = 20;

        /** 单次到期规划分页大小 */
        private int plannerBatchSize = 200;

        /** 最大重试次数 */
        private int maxRetryCount = 3;

        /** 处理中超时秒数 */
        private long timeoutSeconds = 180L;

        /** 初始退避秒数 */
        private long initialBackoffSeconds = 30L;

        /** 最大退避秒数 */
        private long maxBackoffSeconds = 300L;

        /** 工作器轮询间隔毫秒 */
        private long pollDelayMs = 1000L;

        /** 超时恢复扫描间隔毫秒 */
        private long recoveryDelayMs = 30000L;

        /** 应用到期计划 cron */
        private String expiryPlannerCron = "0 */10 * * * ?";
    }
}
