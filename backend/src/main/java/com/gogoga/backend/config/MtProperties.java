package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 美团开放平台配置
 *
 * @author system
 * @since 2026-03-22
 */
@Data
@Component
@ConfigurationProperties(prefix = "mt")
public class MtProperties {

    /**
     * 美团开发者 ID
     */
    private Long developerId;

    /**
     * 美团签名密钥
     */
    private String signKey;

    /**
     * 美团业务 ID
     */
    private Integer businessId = 16;

    /**
     * 授权成功后的跳转地址
     */
    private String callbackSuccessUrl = "https://aia.waimaidashi.com/#/";

    /**
     * Token 刷新配置
     */
    private Token token = new Token();

    /**
     * 美团 H5 鉴权配置
     */
    private H5 h5 = new H5();

    /**
     * 订阅订单拉取配置
     */
    private AppOrder appOrder = new AppOrder();

    /**
     * 推广配置
     */
    private Promotion promotion = new Promotion();

    @Data
    public static class Token {

        /**
         * token 刷新 cron
         */
        private String refreshCron = "0 */10 * * * ?";

        /**
         * 过期前多少秒开始刷新
         */
        private long refreshBeforeExpirySeconds = 1800L;

        /**
         * token 历史配置。
         */
        private History history = new History();
    }

    @Data
    public static class History {

        /**
         * token 历史加密密钥。线上环境应使用独立密钥配置。
         */
        private String encryptionKey = "local-dev-token-history-key-change-me";

        /**
         * token fingerprint HMAC 密钥。为空时复用 encryptionKey。
         */
        private String fingerprintKey;
    }

    @Data
    public static class H5 {

        /**
         * 允许的应用标识，可选
         */
        private String appKey;
    }

    @Data
    public static class AppOrder {

        /**
         * 拉取订阅订单 cron
         */
        private String pullCron = "0 0/30 * * * ?";

        /**
         * 小应用 appId
         */
        private Integer appId = 138;

        /**
         * 分页大小
         */
        private Integer pageSize = 100;

        /**
         * 首次拉取回看秒数
         */
        private long lookbackSeconds = 172800L;
    }

    @Data
    public static class Promotion {

        /**
         * 自动调价 cron
         */
        private String autoBidCron = "0 */10 * * * ?";

        /**
         * 推广写接口是否仅 dry run，不真实调用美团
         */
        private boolean writeDryRun = true;

        /**
         * 数据库异步任务配置
         */
        private AsyncTask asyncTask = new AsyncTask();
    }

    @Data
    public static class AsyncTask {

        /**
         * 是否启用数据库异步任务模式
         */
        private boolean enabled = false;

        /**
         * 是否启用异步 worker 轮询
         */
        private boolean workerEnabled = false;

        /**
         * 调价候选分页大小
         */
        private int dispatchPageSize = 200;

        /**
         * 单次轮询任务数
         */
        private int pollSize = 20;

        /**
         * 轮询间隔毫秒
         */
        private long pollDelayMs = 1000L;

        /**
         * 超时回收间隔毫秒
         */
        private long recoveryDelayMs = 30000L;

        /**
         * 单次回收任务数
         */
        private int recoveryLimit = 20;

        /**
         * worker 线程池核心线程数
         */
        private int executorCorePoolSize = 4;

        /**
         * worker 线程池最大线程数
         */
        private int executorMaxPoolSize = 8;

        /**
         * worker 线程池队列容量
         */
        private int executorQueueCapacity = 200;

        /**
         * 自动调价任务策略
         */
        private TaskPolicy autoBid = new TaskPolicy();

        /**
         * 订单回调任务策略
         */
        private TaskPolicy orderNotify = new TaskPolicy();

        /**
         * 日志 Markdown 任务策略
         */
        private TaskPolicy logMarkdown = new TaskPolicy();

        /**
         * 授权后同步权益任务策略
         */
        private TaskPolicy authSyncRights = new TaskPolicy();

        /**
         * 授权后补齐 poiId 任务策略
         */
        private TaskPolicy authBackfillPoiId = new TaskPolicy();

        /**
         * 授权后同步广告数据任务策略
         */
        private TaskPolicy authSyncRecentAdData = new TaskPolicy();

        /**
         * 授权后历史订单补数任务策略
         */
        private TaskPolicy authHistoricalOrderBackfill = new TaskPolicy();

        /**
         * 同店自动调价锁定秒数
         */
        private long autoBidShopLockSeconds = 300L;

        /**
         * 同订单回调锁定秒数
         */
        private long orderNotifyLockSeconds = 60L;

        /**
         * 同店锁冲突退避秒数
         */
        private long autoBidBusyBackoffSeconds = 30L;

        /**
         * 同订单回调锁冲突退避秒数
         */
        private long orderNotifyBusyBackoffSeconds = 5L;

        /**
         * 历史终态任务清理配置
         */
        private Cleanup cleanup = new Cleanup();
    }

    @Data
    public static class Cleanup {

        /**
         * 是否启用历史终态任务清理
         */
        private boolean enabled = false;

        /**
         * 清理扫描间隔毫秒
         */
        private long delayMs = 3600000L;

        /**
         * 单次每种状态最多清理条数
         */
        private int batchSize = 1000;

        /**
         * SUCCESS 任务保留天数
         */
        private int successRetentionDays = 7;

        /**
         * DEAD 任务保留天数
         */
        private int deadRetentionDays = 30;

        /**
         * 清理任务锁定秒数
         */
        private long lockSeconds = 300L;
    }

    @Data
    public static class TaskPolicy {

        /**
         * 最大重试次数
         */
        private int maxRetryCount = 3;

        /**
         * 任务超时秒数
         */
        private long timeoutSeconds = 300L;

        /**
         * 初始退避秒数
         */
        private long initialBackoffSeconds = 60L;

        /**
         * 最大退避秒数
         */
        private long maxBackoffSeconds = 1800L;
    }
}
