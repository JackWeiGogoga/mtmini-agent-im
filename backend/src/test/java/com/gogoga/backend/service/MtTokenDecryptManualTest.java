package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 美团 token 历史密文手动解密工具。
 *
 * <p>使用方式：</p>
 * <p>1. 从 `mt_access_token_history.access_token_cipher` 或 `refresh_token_cipher` 复制密文</p>
 * <p>2. 设置以下环境变量：</p>
 * <p>   MT_TOKEN_DECRYPT_MANUAL_ENABLED=true</p>
 * <p>   MT_TOKEN_HISTORY_ENCRYPTION_KEY=线上 token 历史加密密钥</p>
 * <p>   MT_TOKEN_HISTORY_CIPHER_TEXT=待解密密文</p>
 * <p>   MT_TOKEN_HISTORY_FINGERPRINT_KEY=线上 token fingerprint 密钥（可选）</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=MtTokenDecryptManualTest test</p>
 */
class MtTokenDecryptManualTest {

    @Test
    void shouldDecryptConfiguredCipherTextWhenManualEnabled() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("MT_TOKEN_DECRYPT_MANUAL_ENABLED")),
                "跳过美团 token 手动解密测试，设置 MT_TOKEN_DECRYPT_MANUAL_ENABLED=true 后再执行。");

        MtProperties properties = new MtProperties();
        properties.getToken().getHistory().setEncryptionKey(requireEnv("MT_TOKEN_HISTORY_ENCRYPTION_KEY"));
        properties.getToken().getHistory().setFingerprintKey(System.getenv("MT_TOKEN_HISTORY_FINGERPRINT_KEY"));

        String cipherText = requireEnv("MT_TOKEN_HISTORY_CIPHER_TEXT");
        assertTrue(cipherText.startsWith("v1:"), "密文格式应以 v1: 开头");

        MtTokenCryptoService service = new MtTokenCryptoService(properties);
        String plainToken = service.decrypt(cipherText);

        assertFalse(plainToken.isBlank(), "解密后的 token 不能为空");
        System.out.printf("mt token decrypt success, token=%s, fingerprint=%s%n",
                plainToken, service.fingerprint(plainToken));
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }
}
