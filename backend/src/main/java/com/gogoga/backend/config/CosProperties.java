package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "cos")
public class CosProperties {

    private boolean enabled = false;

    private String secretId;

    private String secretKey;

    private String region;

    /**
     * 存储桶名称，格式为 bucket-appid。
     */
    private String bucket;

    /**
     * 可选的访问域名，例如 https://static.example.com。
     */
    private String publicAccessUrlBase;

    private ReceiptCode receiptCode = new ReceiptCode();

    @Data
    public static class ReceiptCode {

        private String keyPrefix = "sales/receipt-code";

        /**
         * true 时读取走预签名 URL，false 时直接返回公开访问 URL。
         */
        private boolean privateRead = true;

        private long accessUrlExpireSeconds = 1800;

        private DataSize maxFileSize = DataSize.ofMegabytes(2);

        private List<String> allowedContentTypes = new ArrayList<>(
                List.of("image/png", "image/jpeg", "image/webp")
        );
    }
}
