package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 美团 token 历史加密与指纹工具。
 */
@Service
@RequiredArgsConstructor
public class MtTokenCryptoService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MtProperties mtProperties;

    public String encrypt(String plainText) {
        if (StringUtils.isBlank(plainText)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("美团token历史加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (StringUtils.isBlank(cipherText)) {
            return null;
        }
        String[] parts = cipherText.split(":");
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalArgumentException("不支持的美团token密文格式");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("美团token历史解密失败", e);
        }
    }

    public String fingerprint(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(fingerprintSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("美团token指纹生成失败", e);
        }
    }

    private SecretKeySpec aesKey() {
        byte[] digest = sha256(encryptionSecret());
        return new SecretKeySpec(digest, "AES");
    }

    private String encryptionSecret() {
        MtProperties.History history = mtProperties.getToken().getHistory();
        String key = history == null ? null : history.getEncryptionKey();
        if (StringUtils.isBlank(key)) {
            key = mtProperties.getSignKey();
        }
        if (StringUtils.isBlank(key)) {
            throw new IllegalStateException("mt.token.history.encryption-key 未配置");
        }
        return key;
    }

    private String fingerprintSecret() {
        MtProperties.History history = mtProperties.getToken().getHistory();
        String key = history == null ? null : history.getFingerprintKey();
        return StringUtils.defaultIfBlank(key, encryptionSecret());
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
