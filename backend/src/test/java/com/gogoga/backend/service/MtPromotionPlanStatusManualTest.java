package com.gogoga.backend.service;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adStatusInfo.AdStatusInfoRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adStatusInfo.AdStatusInfoResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团广告计划状态接口手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. MT_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. MT_DEVELOPER_ID=你的开发者 ID</p>
 * <p>3. MT_SIGN_KEY=你的签名密钥</p>
 * <p>4. MT_TEST_ACCESS_TOKEN=商家 accessToken</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtPromotionPlanStatusManualTest test</p>
 */
class MtPromotionPlanStatusManualTest {

    @Test
    void shouldQueryPromotionPlanStatusWhenLiveTestEnabled() throws MtSdkException {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");

        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        MtWaimaiAdDecryptService mtWaimaiAdDecryptService = new MtWaimaiAdDecryptService(meituanClient);
        AdStatusInfoRequest request = new AdStatusInfoRequest();
        request.setChannel(1);

        MeituanResponse<AdStatusInfoResponse> response = meituanClient.invokeApi(request, accessToken);

        assertNotNull(response, "美团 SDK 返回不能为空");
        assertTrue(response.isSuccess(),
                () -> String.format("美团网关调用失败，code=%s, msg=%s", response.getCode(), response.getMsg()));
        assertNotNull(response.getData(), "美团计划状态响应 data 不能为空");
        assertNotNull(response.getData().getResult(), "美团计划状态响应 result 不能为空");
        assertTrue(Boolean.TRUE.equals(response.getData().getResult().getSuccess()),
                () -> String.format("美团计划状态业务失败，code=%s, message=%s",
                        response.getData().getResult().getCode(),
                        response.getData().getResult().getMessage()));

        System.out.printf("plan status success, status=%s, bid=%s, budget=%s, budgetUsed=%s%n",
                response.getData().getStatus(),
                response.getData().getBid(),
                response.getData().getBudget(),
                response.getData().getBudgetUsed());

        String decryptedBudgetUsedCents = response.getData().getBudgetUsed() == null
                ? null
                : mtWaimaiAdDecryptService.decryptValue(null, accessToken, response.getData().getBudgetUsed(), "推广预算已用额度");

        BigDecimal budgetYuan = response.getData().getBudget() == null
                ? null
                : centsToYuan(BigDecimal.valueOf(response.getData().getBudget()));
        BigDecimal budgetUsedYuan = decryptedBudgetUsedCents == null
                ? null
                : centsToYuan(new BigDecimal(decryptedBudgetUsedCents.trim()));
        BigDecimal availableBudgetYuan = budgetYuan == null || budgetUsedYuan == null
                ? null
                : budgetYuan.subtract(budgetUsedYuan).setScale(2, RoundingMode.HALF_UP);

        System.out.printf("plan status converted yuan, bid=%s, budget=%s, budgetUsed=%s, availableBudget=%s%n",
                response.getData().getBid() == null ? null : centsToYuan(BigDecimal.valueOf(response.getData().getBid())),
                budgetYuan,
                budgetUsedYuan,
                availableBudgetYuan);
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }

    private BigDecimal centsToYuan(BigDecimal cents) {
        return cents.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
