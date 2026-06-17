package com.gogoga.backend.service;

import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataInfoList;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团推广广告明细手动联调测试
 */
@SpringBootTest
class MtPromotionAdDataManualTest {

    @Autowired
    private MeituanClient meituanClient;

    @Autowired
    private MtWaimaiAdDecryptService mtWaimaiAdDecryptService;

    @Test
    void shouldQueryAdDataDetailWhenLiveTestEnabled() throws MtSdkException {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");
        String date = System.getenv().getOrDefault(
                "MT_TEST_BIZ_DATE",
                LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        );

        AdDataDetailRequest request = new AdDataDetailRequest();
        request.setChannel(MtPromotionAdDataService.CHANNEL_DIANJIN);
        request.setDate(date);
        MeituanResponse<AdDataDetailResponse> response = meituanClient.invokeApi(request, accessToken);

        System.out.printf("promotion ad detail raw response, date=%s, response=%s%n", date, response);
        assertNotNull(response, "广告明细接口返回不能为空");
        assertTrue(response.isSuccess(),
                () -> String.format("美团网关调用失败，code=%s, msg=%s", response.getCode(), response.getMsg()));
        assertNotNull(response.getData(), "广告明细 data 不能为空");
        assertNotNull(response.getData().getResult(), "广告明细 result 不能为空");
        assertTrue(Boolean.TRUE.equals(response.getData().getResult().getSuccess()),
                () -> String.format("广告明细业务失败，code=%s, message=%s",
                        response.getData().getResult().getCode(),
                        response.getData().getResult().getMessage()));

        Map<String, String> decryptCache = new HashMap<>();
        String show = mtWaimaiAdDecryptService.decryptValue(null, accessToken, response.getData().getShow(), "广告明细日曝光", decryptCache);
        String click = mtWaimaiAdDecryptService.decryptValue(null, accessToken, response.getData().getClick(), "广告明细日点击", decryptCache);
        String debit = mtWaimaiAdDecryptService.decryptValue(null, accessToken, response.getData().getDebit(), "广告明细日消耗", decryptCache);
        System.out.printf("promotion ad detail summary, date=%s, show=%s, click=%s, debitCents=%s, rowCount=%s%n",
                date, show, click, debit,
                response.getData().getAdDataInfoList() == null ? 0 : response.getData().getAdDataInfoList().size());

        if (response.getData().getAdDataInfoList() != null) {
            for (AdDataInfoList row : response.getData().getAdDataInfoList()) {
                String rowShow = mtWaimaiAdDecryptService.decryptValue(null, accessToken, row.getShow(), "广告明细分时曝光", decryptCache);
                String rowClick = mtWaimaiAdDecryptService.decryptValue(null, accessToken, row.getClick(), "广告明细分时点击", decryptCache);
                String rowDebit = mtWaimaiAdDecryptService.decryptValue(null, accessToken, row.getDebit(), "广告明细分时消耗", decryptCache);
                System.out.printf("promotion ad detail row, time=%s, show=%s, click=%s, debitCents=%s%n",
                        row.getTime(), rowShow, rowClick, rowDebit);
            }
        }
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }
}
