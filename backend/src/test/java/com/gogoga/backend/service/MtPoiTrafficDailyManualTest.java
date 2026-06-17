package com.gogoga.backend.service;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptResponse;
import com.meituan.sdk.model.wmoperNg.poi.getPoiExtendInfo.GetPoiExtendInfoRequest;
import com.meituan.sdk.model.wmoperNg.poi.getPoiExtendInfo.GetPoiExtendInfoResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Item;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Prop;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团营业分析流量曝光与入店接口手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. MT_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. MT_DEVELOPER_ID=你的开发者 ID</p>
 * <p>3. MT_SIGN_KEY=你的签名密钥</p>
 * <p>4. MT_TEST_ACCESS_TOKEN=商家 accessToken</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtPoiTrafficDailyManualTest test</p>
 *
 * @author system
 * @since 2026-04-10
 */
class MtPoiTrafficDailyManualTest {

    private static final int FLOW_PROP_NATURAL = 1;
    private static final int FLOW_PROP_PAID = 2;
    private static final int FLOW_PROP_ALL = 3;

    @Test
    void shouldQueryPoiTrafficWhenLiveTestEnabled() throws MtSdkException {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");

        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        Map<Integer, TrafficSummary> summaries = new LinkedHashMap<>();
        for (int flowProp : List.of(FLOW_PROP_NATURAL, FLOW_PROP_PAID, FLOW_PROP_ALL)) {
            summaries.put(flowProp, queryTrafficSummary(meituanClient, accessToken, flowProp));
        }

        TrafficSummary natural = summaries.get(FLOW_PROP_NATURAL);
        TrafficSummary paid = summaries.get(FLOW_PROP_PAID);
        TrafficSummary all = summaries.get(FLOW_PROP_ALL);
        if (!natural.hasData() || !paid.hasData() || !all.hasData()) {
            System.out.printf("skip compare because some flowProp has no data, natural=%s, paid=%s, all=%s%n",
                    natural, paid, all);
            return;
        }

        int sumExposure = natural.exposureCount() + paid.exposureCount();
        int sumVisit = natural.visitCount() + paid.visitCount();
        System.out.printf("compare flowProp summary, natural+paid exposure=%s, all exposure=%s, " +
                        "natural+paid visit=%s, all visit=%s%n",
                sumExposure, all.exposureCount(), sumVisit, all.visitCount());
        assertTrue(sumExposure == all.exposureCount() && sumVisit == all.visitCount(),
                () -> String.format("flowProp 对比不相等，natural=%s, paid=%s, all=%s",
                        natural, paid, all));
    }

    @Test
    void shouldQueryPoiExtendInfoWhenLiveTestEnabled() throws MtSdkException {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");

        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        long startTime = System.currentTimeMillis();
        MeituanResponse<GetPoiExtendInfoResponse> response = assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> meituanClient.invokeApi(new GetPoiExtendInfoRequest(), accessToken),
                "调用门店扩展信息接口超时，30 秒内未返回"
        );

        System.out.printf("poi extend info raw response, durationMs=%s, response=%s%n",
                System.currentTimeMillis() - startTime,
                response);

        assertNotNull(response, "美团门店扩展信息接口返回不能为空");
        assertTrue(response.isSuccess(),
                () -> String.format("美团门店扩展信息网关失败，code=%s, msg=%s", response.getCode(), response.getMsg()));
        assertNotNull(response.getData(), "美团门店扩展信息响应 data 不能为空");

        GetPoiExtendInfoResponse payload = response.getData();
        System.out.printf("poi extend info payload, qrCodeUrl=%s, h5LinkUrl=%s, miniProgramUrl=%s%n",
                payload.getQrCodeUrl(),
                payload.getH5LinkUrl(),
                payload.getMiniProgramUrl());
    }

    private TrafficSummary queryTrafficSummary(MeituanClient meituanClient, String accessToken, int flowProp)
            throws MtSdkException {
        PoiAdEffectdataRequest request = new PoiAdEffectdataRequest();
        request.setFlowProp(flowProp);
        request.setDays(1);
        long startTime = System.currentTimeMillis();
        MeituanResponse<PoiAdEffectdataResponse> response = assertTimeoutPreemptively(
                Duration.ofSeconds(30),
                () -> meituanClient.invokeApi(request, accessToken),
                "调用营业分析流量接口超时，30 秒内未返回"
        );

        System.out.printf("poi traffic raw response, flowProp=%s, durationMs=%s, response=%s%n",
                flowProp,
                System.currentTimeMillis() - startTime,
                response);

        assertNotNull(response, "美团营业分析流量接口返回不能为空");
        assertTrue(response.isSuccess(),
                () -> String.format("美团网关调用失败，flowProp=%s, code=%s, msg=%s",
                        flowProp, response.getCode(), response.getMsg()));
        assertNotNull(response.getData(), "美团营业分析流量响应 data 不能为空");
        assertNotNull(response.getData().getResult(), "美团营业分析流量响应 result 不能为空");
        System.out.printf("poi traffic business result, flowProp=%s, code=%s, message=%s%n",
                flowProp,
                response.getData().getResult().getCode(),
                response.getData().getResult().getMessage());
        assertTrue(Boolean.TRUE.equals(response.getData().getResult().getSuccess()),
                () -> String.format("美团营业分析流量业务失败，flowProp=%s, code=%s, message=%s",
                        flowProp,
                        response.getData().getResult().getCode(),
                        response.getData().getResult().getMessage()));

        int exposureCount = 0;
        int visitCount = 0;
        if (response.getData().getData() == null) {
            return new TrafficSummary(flowProp, false, 0, 0);
        }
        for (Item item : response.getData().getData()) {
            System.out.printf("poi traffic dt=%s, flowProp=%s%n", item.getDt(), flowProp);
            if (item.getData() == null) {
                continue;
            }
            for (Prop prop : item.getData()) {
                String show = decryptValue(meituanClient, accessToken, prop.getShow());
                String click = decryptValue(meituanClient, accessToken, prop.getClick());
                System.out.printf("parsed traffic flowProp=%s, pos=%s, show=%s, click=%s%n",
                        prop.getFlowProp(),
                        prop.getPos(),
                        show,
                        click);
                exposureCount += Integer.parseInt(show.trim());
                visitCount += Integer.parseInt(click.trim());
            }
        }
        System.out.printf("poi traffic summary, flowProp=%s, exposure=%s, visit=%s%n",
                flowProp, exposureCount, visitCount);
        return new TrafficSummary(flowProp, true, exposureCount, visitCount);
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }

    private String decryptValue(MeituanClient meituanClient, String accessToken, String encryptedValue)
            throws MtSdkException {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return encryptedValue;
        }
        try {
            Integer.parseInt(encryptedValue.trim());
            System.out.printf("metric value is plain numeric, value=%s%n", encryptedValue);
            return encryptedValue;
        } catch (NumberFormatException ignored) {
            DecryptRequest request = new DecryptRequest();
            request.setEncryptedValue(encryptedValue);
            MeituanResponse<DecryptResponse> response = meituanClient.invokeApi(request, accessToken);

            System.out.printf("decrypt metric raw response, encryptedValue=%s, response=%s%n",
                    encryptedValue, response);

            assertNotNull(response, "美团解密接口返回不能为空");
            assertTrue(response.isSuccess(),
                    () -> String.format("美团解密接口网关失败，code=%s, msg=%s", response.getCode(), response.getMsg()));
            assertNotNull(response.getData(), "美团解密接口响应 data 不能为空");
            assertNotNull(response.getData().getResult(), "美团解密接口响应 result 不能为空");
            System.out.printf("decrypt metric business result, code=%s, message=%s, value=%s%n",
                    response.getData().getResult().getCode(),
                    response.getData().getResult().getMessage(),
                    response.getData().getValue());
            assertTrue(Boolean.TRUE.equals(response.getData().getResult().getSuccess()),
                    () -> String.format("美团解密接口业务失败，code=%s, message=%s",
                            response.getData().getResult().getCode(),
                            response.getData().getResult().getMessage()));
            return response.getData().getValue();
        }
    }

    private record TrafficSummary(int flowProp, boolean hasData, int exposureCount, int visitCount) {
    }
}
