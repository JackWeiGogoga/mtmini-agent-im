package com.gogoga.backend.service;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adBalanceInfo.AdBalanceInfoResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团广告余额接口手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. MT_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. MT_DEVELOPER_ID=你的开发者 ID</p>
 * <p>3. MT_SIGN_KEY=你的签名密钥</p>
 * <p>4. MT_TEST_ACCESS_TOKEN=商家 accessToken</p>
 *
 * <p>执行命令：</p>
 * <p>mvn -Dtest=MtAdBalanceManualTest test</p>
 *
 * @author system
 * @since 2026-03-27
 */
class MtAdBalanceManualTest {

    @Test
    void shouldQueryAdBalanceWhenLiveTestEnabled() throws MtSdkException {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");

        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        MtWaimaiAdDecryptService mtWaimaiAdDecryptService = new MtWaimaiAdDecryptService(meituanClient);
        MeituanResponse<AdBalanceInfoResponse> response = meituanClient.invokeApi(new AdBalanceInfoRequest(), accessToken);

        assertNotNull(response, "美团 SDK 返回不能为空");
        assertTrue(response.isSuccess(),
                () -> String.format("美团网关调用失败，code=%s, msg=%s", response.getCode(), response.getMsg()));
        assertNotNull(response.getData(), "美团广告余额响应 data 不能为空");
        assertNotNull(response.getData().getResult(), "美团广告余额响应 result 不能为空");
        assertTrue(Boolean.TRUE.equals(response.getData().getResult().getSuccess()),
                () -> String.format("美团广告余额业务失败，code=%s, message=%s",
                        response.getData().getResult().getCode(),
                        response.getData().getResult().getMessage()));

        System.out.printf("ad balance success, primaryBalance=%s, commonBalance=%s, debit=%s%n",
                response.getData().getPrimaryBalance(),
                response.getData().getCommonBalance(),
                response.getData().getDebit());

        String primaryBalanceCents = mtWaimaiAdDecryptService.decryptValue(null, accessToken, response.getData().getPrimaryBalance(), "美团广告金额");
        String commonBalanceCents = mtWaimaiAdDecryptService.decryptValue(null, accessToken, response.getData().getCommonBalance(), "美团广告金额");
        String debitCents = mtWaimaiAdDecryptService.decryptValue(null, accessToken, response.getData().getDebit(), "美团广告金额");

        System.out.printf("ad balance decrypted cents, primaryBalance=%s, commonBalance=%s, debit=%s%n",
                primaryBalanceCents, commonBalanceCents, debitCents);
        System.out.printf("ad balance converted yuan, primaryBalance=%s, commonBalance=%s, debit=%s%n",
                centsToYuan(primaryBalanceCents),
                centsToYuan(commonBalanceCents),
                centsToYuan(debitCents));
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }

    private BigDecimal centsToYuan(String cents) {
        return new BigDecimal(cents.trim()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
