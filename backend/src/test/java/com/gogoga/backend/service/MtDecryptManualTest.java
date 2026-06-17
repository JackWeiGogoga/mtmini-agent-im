package com.gogoga.backend.service;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团解密接口手动联调测试
 *
 * <p>使用方式：</p>
 * <p>1. 直接修改 TEST_ENCRYPTED_VALUE 为待解密字符串</p>
 * <p>2. 设置以下环境变量：</p>
 * <p>   MT_TEST_ENABLE_LIVE_API=true</p>
 * <p>   MT_DEVELOPER_ID=你的开发者 ID</p>
 * <p>   MT_SIGN_KEY=你的签名密钥</p>
 * <p>   MT_TEST_ACCESS_TOKEN=商家 accessToken</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtDecryptManualTest test</p>
 */
class MtDecryptManualTest {

    private static final String TEST_ENCRYPTED_VALUE = "7ajzeNT/dpRAgOBlItrmTA==";

    @Test
    void shouldDecryptConfiguredValueWhenLiveTestEnabled() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TEST_ENABLE_LIVE_API")),
                "跳过真实美团接口测试，设置 MT_TEST_ENABLE_LIVE_API=true 后再执行。");

        assertTrue(!TEST_ENCRYPTED_VALUE.isBlank() && !"请替换为待解密字符串".equals(TEST_ENCRYPTED_VALUE),
                "请先修改 TEST_ENCRYPTED_VALUE 为待解密字符串");

        Long developerId = Long.valueOf(requireEnv("MT_DEVELOPER_ID"));
        String signKey = requireEnv("MT_SIGN_KEY");
        String accessToken = requireEnv("MT_TEST_ACCESS_TOKEN");

        MeituanClient meituanClient = DefaultMeituanClient.builder(developerId, signKey).build();
        MtWaimaiAdDecryptService decryptService = new MtWaimaiAdDecryptService(meituanClient);
        String decryptedValue = decryptService.decryptValue(null, accessToken, TEST_ENCRYPTED_VALUE, "手动解密");

        System.out.printf("decrypt success, encryptedValue=%s, decryptedValue=%s%n",
                TEST_ENCRYPTED_VALUE, decryptedValue);
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }
}
