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
    private String callbackSuccessUrl = "https://aic.waimaidashi.com/#/";

    /**
     * 美团 SDK 出站调用配置。
     */
    private Api api = new Api();

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
     * IM 消息配置。
     */
    private Im im = new Im();

    /**
     * IM 多轮自动回复配置。
     */
    private ImAutoReply imAutoReply = new ImAutoReply();

    /**
     * IM 定时消息群发配置。
     */
    private ImBroadcast imBroadcast = new ImBroadcast();

    /**
     * 通用敏感词配置。
     */
    private SensitiveWord sensitiveWord = new SensitiveWord();

    /**
     * 通用门店知识库配置。
     */
    private Knowledge knowledge = new Knowledge();

    @Data
    public static class Api {

        /**
         * 是否阻断全部美团 SDK 出站调用。安全起见默认开启。
         */
        private boolean dryRun = false;

        /**
         * 是否单独阻断 IM 消息发送调用。在总开关之上额外收紧，默认关闭；
         * 总开关开启时 IM 发送同样被阻断，与此开关取值无关。
         */
        private boolean imSendDryRun = true;

        /**
         * 数据库异步任务因 dry run 跳过后的无损重排秒数。
         */
        private long dryRunRescheduleSeconds = 3600L;
    }

    @Data
    public static class Im {
        /**
         * 美团 IM 消息 AES 密钥，UTF-8 编码必须恰好 16 字节。
         */
        private String secret;

        /**
         * IM 回调携带的 businessId。
         *
         * <p>与 OAuth / token 刷新 / H5 鉴权使用的 {@code mt.business-id} 不是同一个值：
         * 后者是外卖业务线（16），而 IM 回调走独立业务线（2）。两者必须分开配置，
         * 否则改动一侧会连带破坏另一侧。</p>
         */
        private Integer callbackBusinessId = 2;

        /**
         * 验签失败时是否输出完整原始回调参数。默认关闭。
         *
         * <p>开启后日志会包含消息密文和完整 sign，只应在测试门店联调期间短暂启用，
         * 排查结束必须关回 false。</p>
         */
        private boolean callbackDebugLog = false;
    }

    @Data
    public static class ImAutoReply {

        /**
         * 全局编排总开关。默认关闭，发布后可按环境灰度开启。
         */
        private boolean enabled = false;

        /**
         * 连续多少分钟无消息后开启新的短期记忆 Session。
         */
        private long sessionIdleMinutes = 30L;

        /**
         * 提供给模型的当前 Session 最大历史轮数。
         */
        private int historyMaxRounds = 100;

        /**
         * 连续用户消息防抖时间。
         */
        private long debounceMillis = 2000L;

        /**
         * 商家人工发言后的静默等待秒数。该窗口内不抢答，把接待机会留给人工；
         * 窗口到期且商家未继续跟进时，AI 接管回复。
         */
        private long merchantHoldSeconds = 40L;

        /**
         * 回复生成后的随机发送延迟下限秒数，避免秒回显得机械。
         */
        private long sendDelayMinSeconds = 3L;

        /**
         * 回复生成后的随机发送延迟上限秒数。
         */
        private long sendDelayMaxSeconds = 10L;

        /**
         * 模型上下文中消息正文的最大字符预算。
         */
        private int maxContextChars = 24000;

        /**
         * 单次回复最大输出 token。
         */
        private int maxCompletionTokens = 512;

        /**
         * Chat 生成温度。
         */
        private double temperature = 0.3D;

        /**
         * RAG 检索时拼接的最近用户消息条数。
         */
        private int ragRecentUserMessages = 3;

        /**
         * Conversation 分布式锁租约秒数。
         */
        private long lockLeaseSeconds = 60L;

        /**
         * Conversation 分布式锁续租间隔秒数。
         */
        private long lockRenewSeconds = 20L;

        /**
         * Chat 模型供应商。
         */
        private String modelProvider = "TOKENHUB";

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
         * 敏感词改写仍失败时使用的固定安全回复。
         */
        private String safeFallbackReply = "抱歉，我暂时无法准确回答这个问题，请联系门店人工客服。";

        /**
         * 顾客发来图片等非文本消息时的固定兜底回复。这类消息不做多模态解析、不调用大模型，
         * 直接用本话术占位，再按常规逻辑拼接门店联系方式。
         */
        private String nonTextFallbackReply = "商家正在忙";
    }

    @Data
    public static class ImBroadcast {

        /**
         * 群发总开关。安全起见默认关闭。
         */
        private boolean enabled = false;

        /**
         * 调度扫描 cron，默认每分钟。
         */
        private String scheduleCron = "0 * * * * ?";

        /**
         * 调度锁租约秒数。
         */
        private long scheduleLockSeconds = 50L;

        /**
         * 单轮调度最多处理的门店配置数。
         */
        private int schedulePageSize = 200;

        /**
         * 目标人群活跃回溯天数。
         */
        private int activeWithinDays = 30;

        /**
         * 单批次目标人数上限，超出部分按最近活跃截断。
         */
        private int maxTargetsPerBatch = 5000;

        /**
         * 目标人群游标翻页大小。
         */
        private int planPageSize = 500;

        /**
         * 每个发送分片的目标人数。
         */
        private int shardSize = 500;

        /**
         * 触发时按 shopId 哈希打散的最大抖动秒数，用于削平整点洪峰。
         */
        private int triggerJitterSeconds = 60;

        /**
         * 群发独立线程池并发度。
         */
        private int executorConcurrency = 4;

        /**
         * 单门店同时执行的发送分片数，控制单店对美团的并发。
         */
        private int shopConcurrency = 1;

        /**
         * 全局发送 QPS 上限。频控未知时保守取值。
         */
        private int globalQps = 20;

        /**
         * 命中上游频控后的退避毫秒数。
         */
        private long throttleBackoffMillis = 2000L;

        /**
         * 是否把群发消息写入会话时间线，保证顾客回复时 AI 上下文完整。
         */
        private boolean persistTimelineMessage = true;

        /**
         * 单条文案最大字数。
         */
        private int maxContentLength = 100;

        /**
         * AI 文案优化设置。
         */
        private ContentOptimize contentOptimize = new ContentOptimize();

        /**
         * 编排任务策略。
         */
        private TaskPolicy planTask = new TaskPolicy();

        /**
         * 分片发送任务策略。
         */
        private TaskPolicy sendTask = new TaskPolicy();
    }

    @Data
    public static class ContentOptimize {

        /**
         * 系统提示词。{maxLength} 会被替换为文案上限字数。
         */
        private String systemPrompt = "你是一名资深的外卖文案策划。优化用户给出的外卖群发文案，"
                + "限制在{maxLength}字以内，语气亲切、带表情符号，只输出优化后的文案正文，"
                + "不要解释、不要加引号、不要输出多余内容。";

        /**
         * 命中敏感词后自动重写一次的提示词，追加在原提示词之后。
         */
        private String sensitiveRewriteHint = "上一版文案包含敏感信息，请在保持原意的前提下改写为合规文案，"
                + "同样限制在{maxLength}字以内、带表情符号，只输出文案正文。";

        /**
         * 采样温度。文案优化允许比客服回复更活泼一些。
         */
        private double temperature = 0.7D;

        /**
         * 单次优化输出的最大 token 数。
         */
        private int maxCompletionTokens = 400;
    }

    @Data
    public static class SensitiveWord {

        /**
         * 是否启用敏感词检测。
         */
        private boolean enabled = true;

        /**
         * 预留给后续 IM 回复生成流程的最大改写次数。
         */
        private int maxRewriteAttempts = 1;
    }

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
        private Integer appId = 154;

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
         * 每节点 LLM 任务执行并发；待执行任务保留在数据库。
         */
        private int llmExecutorConcurrency = 2;

        /**
         * LLM 任务类型调度权重。
         */
        private LlmTaskWeights llmTaskWeights = new LlmTaskWeights();

        /**
         * 授权后同步权益任务策略
         */
        private TaskPolicy authSyncRights = new TaskPolicy();

        /**
         * 授权后补齐 poiId 任务策略
         */
        private TaskPolicy authBackfillPoiId = new TaskPolicy();

        /**
         * 授权后初始化门店 IM 任务策略。
         */
        private TaskPolicy authConfigureIm = new TaskPolicy();

        /**
         * 知识文档解析任务策略
         */
        private TaskPolicy knowledgeDocumentParse = new TaskPolicy();

        /**
         * IM 自动回复任务策略。
         */
        private TaskPolicy imAutoReply = new TaskPolicy();

        /**
         * 历史终态任务清理配置
         */
        private Cleanup cleanup = new Cleanup();

    }

    @Data
    public static class LlmTaskWeights {
        private int knowledgeDocumentParse = 1;
        private int imAutoReply = 5;
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
         * 进入回复 prompt 的最低知识片段余弦相似度。
         */
        private double minContextCosineScore = 0.5D;

        /**
         * 进入 RAG 前要求门店余额大于该值。
         */
        private int ragBalanceThreshold = 10000;

        /**
         * 授权成功赠送算力。
         */
        private int authGrantPowerAmount = 800000;

        /**
         * Chat 模型供应商。
         */
        private String modelProvider = "TOKENHUB";

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
        private String documentKeyPrefix = "knowledge/documents";
    }
}
