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
    private String callbackSuccessUrl = "https://aib.waimaidashi.com/#/";

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
     * 数据库异步任务配置
     */
    private AsyncTask asyncTask = new AsyncTask();

    /**
     * 评价自动回复配置
     */
    private Comment comment = new Comment();

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
    public static class AsyncTask {

        /**
         * 是否启用数据库异步任务模式
         */
        private boolean enabled = true;

        /**
         * 是否启用异步 worker 轮询
         */
        private boolean workerEnabled = true;

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
         * 授权后同步权益任务策略
         */
        private TaskPolicy authSyncRights = new TaskPolicy();

        /**
         * 授权后评价回补任务策略
         */
        private TaskPolicy authCommentBackfill = new TaskPolicy();

        /**
         * 评价自动回复任务策略
         */
        private TaskPolicy commentAutoReply = new TaskPolicy();

        /**
         * 知识文档解析任务策略
         */
        private TaskPolicy knowledgeDocumentParse = new TaskPolicy();

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

    @Data
    public static class Comment {

        /**
         * 评价拉取 cron。默认每天早中晚各一次。
         */
        private String pullCron = "0 0 8,13,19 * * ?";

        /**
         * 查询窗口回看天数。
         */
        private int lookbackDays = 7;

        /**
         * 查询分页大小，美团最大 20。
         */
        private int pageSize = 20;

        /**
         * 评价查询 dry run。开启后不调用美团查询评价接口。
         */
        private boolean queryDryRun = false;

        /**
         * 评价回复 dry run。开启后不调用美团添加商家回复接口。
         */
        private boolean replyDryRun = false;

        /**
         * dry run 命中已有异步回复任务时的无损重排秒数。
         */
        private long dryRunRescheduleSeconds = 3600L;

        /**
         * 自动回复固定文案。
         */
        private String defaultReply = "很好";

        /**
         * 评论知识库与 RAG 回复配置。
         */
        private Knowledge knowledge = new Knowledge();
    }

    @Data
    public static class Knowledge {

        /**
         * 是否启用知识库 RAG 回复。
         */
        private boolean enabled = true;

        /**
         * 每个商户手工 QA 最大条数。
         */
        private int maxQaItemsPerShop = 500;

        /**
         * 每个商户最大文档数。
         */
        private int maxDocumentsPerShop = 10;

        /**
         * 单个文档最大大小，字节。默认 20MB。
         */
        private long maxDocumentBytes = 20L * 1024L * 1024L;

        /**
         * 每个文档最大切片数。
         */
        private int maxChunksPerDocument = 500;

        /**
         * 单次候选召回数量。
         */
        private int maxSearchCandidates = 1000;

        /**
         * RAG topK。
         */
        private int topK = 5;

        /**
         * 进入 RAG 前要求门店余额大于该值。
         */
        private int ragBalanceThreshold = 100000;

        /**
         * 授权成功赠送算力。
         */
        private int authGrantPowerAmount = 800000;

        /**
         * Chat OpenAI-compatible base URL。
         */
        private String modelBaseUrl = "https://tokenhub.tencentmaas.com/v1";

        /**
         * Chat OpenAI-compatible API Key。
         */
        private String modelApiKey;

        /**
         * Chat 模型。
         */
        private String chatModel = "deepseek-v4-flash";

        /**
         * 向量提供方。
         */
        private String embeddingProvider = "DASHSCOPE";

        /**
         * Embedding OpenAI-compatible base URL。
         */
        private String embeddingBaseUrl = "https://llm-kz8j7o34hwdm6nb4.cn-beijing.maas.aliyuncs.com/compatible-mode/v1";

        /**
         * Embedding OpenAI-compatible API Key。
         */
        private String embeddingApiKey;

        /**
         * 向量模型。默认使用阿里百炼 text-embedding-v4。
         */
        private String embeddingModel = "text-embedding-v4";

        /**
         * 向量维度。
         */
        private int embeddingDim = 1024;

        /**
         * 知识文档 COS key 前缀。
         */
        private String documentKeyPrefix = "comment-knowledge/documents";

        /**
         * 是否启用评价图片多模态识别。
         */
        private boolean visionEnabled = true;

        /**
         * 图片识别 OpenAI-compatible base URL。
         */
        private String visionModelBaseUrl = "https://tokenhub.tencentmaas.com/v1";

        /**
         * 图片识别 OpenAI-compatible API Key。
         */
        private String visionModelApiKey;

        /**
         * 图片识别模型。
         */
        private String visionModel = "qwen3.5-flash";

        /**
         * 单条评价最多识别图片数量。
         */
        private int visionMaxImageCount = 3;

        /**
         * 图片识别超时时间，秒。
         */
        private long visionTimeoutSeconds = 30L;

        /**
         * 图片识别 usage 缺失时的兜底算力消耗。
         */
        private int visionFallbackPowerAmount = 1000;

        /**
         * 测试图片上传 COS key 前缀。
         */
        private String visionImageKeyPrefix = "comment-knowledge/vision-images";

        /**
         * RAG 回复估算算力消耗，后续可按真实 token 计费替换。
         */
        private int fallbackRagPowerAmount = 1000;
    }
}
