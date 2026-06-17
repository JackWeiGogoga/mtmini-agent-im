package com.gogoga.backend.service;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.PeerAdBidpriceRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.PeerAdBidpriceResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团附近商家平均出价接口手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. MT_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. MT_DEVELOPER_ID=你的开发者 ID</p>
 * <p>3. MT_SIGN_KEY=你的签名密钥</p>
 * <p>4. MT_TEST_ACCESS_TOKEN=商家 accessToken</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtPeerBidpriceManualTest test</p>
 */
class MtPeerBidpriceManualTest {

    @Test
    void shouldQueryPeerAverageBidWhenLiveTestEnabled() throws MtSdkException {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");

        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        MtWaimaiAdDecryptService mtWaimaiAdDecryptService = new MtWaimaiAdDecryptService(meituanClient);
        PeerAdBidpriceRequest request = new PeerAdBidpriceRequest();

        MeituanResponse<PeerAdBidpriceResponse> response = meituanClient.invokeApi(request, accessToken);

        assertNotNull(response, "美团 SDK 返回不能为空");
        assertTrue(response.isSuccess(),
                () -> String.format("美团网关调用失败，code=%s, msg=%s", response.getCode(), response.getMsg()));
        assertNotNull(response.getData(), "附近商家平均出价响应 data 不能为空");
        assertNotNull(response.getData().getResult(), "附近商家平均出价响应 result 不能为空");
        assertTrue(Boolean.TRUE.equals(response.getData().getResult().getSuccess()),
                () -> String.format("附近商家平均出价业务失败，code=%s, message=%s",
                        response.getData().getResult().getCode(),
                        response.getData().getResult().getMessage()));
        assertTrue(response.getData().getAveragePrice() != null && !response.getData().getAveragePrice().isBlank(),
                "附近商家平均出价 averagePrice 不能为空");

        String encryptedAveragePrice = response.getData().getAveragePrice().trim();
        String decryptedAveragePrice = mtWaimaiAdDecryptService.decryptValue(
                null,
                accessToken,
                encryptedAveragePrice,
                "附近商家平均出价"
        );
        BigDecimal averagePriceFen = new BigDecimal(decryptedAveragePrice.trim());
        BigDecimal averagePriceYuan = averagePriceFen.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        System.out.printf("peer bid price success, averagePriceEncrypted=%s, averagePriceFen=%s, averagePriceYuan=%s%n",
                encryptedAveragePrice,
                averagePriceFen.toPlainString(),
                averagePriceYuan.toPlainString());
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }
}
