package com.gogoga.backend.client;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.model.wmoperNg.waimaiad.adAppBuyInfo.AdAppBuyInfoResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团商家购买应用信息查询手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. MT_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. MT_DEVELOPER_ID=你的开发者 ID</p>
 * <p>3. MT_SIGN_KEY=你的签名密钥</p>
 * <p>4. MT_TEST_ACCESS_TOKEN=商家 accessToken</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtAppBuyInfoOpenApiClientManualTest test</p>
 */
class MtAppBuyInfoOpenApiClientManualTest {

    @Test
    void shouldQueryAppBuyInfoWhenLiveTestEnabled() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");

        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        MtAppBuyInfoOpenApiClient client = new MtAppBuyInfoOpenApiClient(meituanClient);

        Optional<AdAppBuyInfoResponse> buyInfo = client.queryBuyInfo(accessToken);

        assertTrue(buyInfo.isPresent(), "查询美团商家购买应用信息失败，返回结果为空");
        System.out.printf("app buy info=%s%n", buyInfo.get());
        System.out.printf("app buy info version=%s, endTime=%s, result=%s%n",
                buyInfo.get().getVersion(),
                buyInfo.get().getEndTime(),
                buyInfo.get().getResult());
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }
}
