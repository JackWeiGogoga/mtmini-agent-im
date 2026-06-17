package com.gogoga.backend.client;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团关闭消息提醒手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. MT_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. MT_DEVELOPER_ID=开发者 ID</p>
 * <p>3. MT_SIGN_KEY=签名密钥</p>
 * <p>4. MT_TEST_ACCESS_TOKEN=商家 accessToken</p>
 * <p>5. MT_TEST_OP_BIZ_CODE=授权商户 opBizCode，可选，默认 manual-test</p>
 * <p>6. MT_TEST_BLOCK_MSG_LIST=逗号分隔的消息类型；默认 16102。传空字符串表示 []，可用于打开订单推送</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtUpdateBlockMsgListClientManualTest test</p>
 */
class MtUpdateBlockMsgListClientManualTest {

    @Test
    void shouldUpdateAndQueryBlockMsgListWhenLiveTestEnabled() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        MtUpdateBlockMsgListClient client = createClient();
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");
        String opBizCode = System.getenv().getOrDefault("MT_TEST_OP_BIZ_CODE", "manual-test");
        List<Integer> blockMsgList = parseBlockMsgList(System.getenv("MT_TEST_BLOCK_MSG_LIST"));
        MtUpdateBlockMsgListClient.UpdateBlockMsgListResult result =
                client.updateBlockMsgListResult(accessToken, opBizCode, blockMsgList);

        System.out.printf("setBlockMsgList result, opBizCode=%s, blockMsgList=%s, success=%s, code=%s, msg=%s, traceId=%s, response=%s%n",
                opBizCode,
                result.blockMsgList(),
                result.success(),
                result.code(),
                result.msg(),
                result.traceId(),
                result.response());
        assertTrue(result.success(), () -> "设置美团消息屏蔽列表失败，opBizCode=" + opBizCode + ", blockMsgList=" + blockMsgList
                + ", code=" + result.code() + ", msg=" + result.msg() + ", traceId=" + result.traceId());

        MtUpdateBlockMsgListClient.QueryBlockMsgListResult queryResult = client.queryBlockMsgListResult(accessToken, opBizCode);
        System.out.printf("queryBlockMsgList result, opBizCode=%s, success=%s, code=%s, msg=%s, traceId=%s, blockMsgList=%s, response=%s%n",
                opBizCode,
                queryResult.success(),
                queryResult.code(),
                queryResult.msg(),
                queryResult.traceId(),
                queryResult.blockMsgList(),
                queryResult.response());
        assertTrue(queryResult.success(), () -> "查询美团消息屏蔽列表失败，opBizCode=" + opBizCode
                + ", code=" + queryResult.code() + ", msg=" + queryResult.msg() + ", traceId=" + queryResult.traceId());
    }

    @Test
    void shouldQueryBlockMsgListWhenLiveTestEnabled() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        MtUpdateBlockMsgListClient client = createClient();
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");
        String opBizCode = System.getenv().getOrDefault("MT_TEST_OP_BIZ_CODE", "manual-test");

        MtUpdateBlockMsgListClient.QueryBlockMsgListResult result = client.queryBlockMsgListResult(accessToken, opBizCode);
        System.out.printf("queryBlockMsgList result, opBizCode=%s, success=%s, code=%s, msg=%s, traceId=%s, blockMsgList=%s, response=%s%n",
                opBizCode,
                result.success(),
                result.code(),
                result.msg(),
                result.traceId(),
                result.blockMsgList(),
                result.response());
        assertTrue(result.success(), () -> "查询美团消息屏蔽列表失败，opBizCode=" + opBizCode
                + ", code=" + result.code() + ", msg=" + result.msg() + ", traceId=" + result.traceId());
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }

    private MtUpdateBlockMsgListClient createClient() {
        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        return new MtUpdateBlockMsgListClient(meituanClient);
    }

    private List<Integer> parseBlockMsgList(String rawValue) {
        if (rawValue == null) {
            return List.of(16102);
        }
        if (rawValue.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }
}
