package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 有赞开放平台配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "youzan")
public class YouzanProperties {

    /**
     * 有赞应用 client_id
     */
    private String clientId;

    /**
     * 有赞应用 client_secret
     */
    private String clientSecret;

    /**
     * 有赞 token 接口地址
     */
    private String tokenUrl = "https://open.youzanyun.com/auth/token";

    /**
     * 有赞回调配置
     */
    private Callback callback = new Callback();

    /**
     * token 刷新配置
     */
    private Token token = new Token();

    /**
     * 权益配置
     */
    private Right right = new Right();

    @Data
    public static class Callback {

        /**
         * message URL decode 编码
         */
        private String urlCharset = "GBK";
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
         * HTTP 超时时间，毫秒
         */
        private int timeoutMillis = 5000;
    }

    @Data
    public static class Right {

        /**
         * 有赞订单核销后授予的推广权益编码
         */
        private String promotionServiceCode = "H5_PROMOTION";

        /**
         * 特殊美团规格映射的仅打开应用权益编码
         */
        private String appOpenOnlyServiceCode = "MT_APP_OPEN_ONLY";
    }
}
