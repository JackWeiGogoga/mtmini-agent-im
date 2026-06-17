package com.gogoga.backend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "cos", name = "enabled", havingValue = "true")
    public COSClient cosClient(CosProperties properties) {
        if (StringUtils.isAnyBlank(
                properties.getSecretId(),
                properties.getSecretKey(),
                properties.getRegion(),
                properties.getBucket())) {
            throw new IllegalStateException("COS 配置不完整，请检查 cos.secret-id/cos.secret-key/cos.region/cos.bucket");
        }
        COSCredentials credentials = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        return new COSClient(credentials, clientConfig);
    }
}
