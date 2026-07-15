package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 美团评价匿名群聊消息加密服务。
 */
@Service
@RequiredArgsConstructor
public class MtImMessageCryptoService {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private final MtProperties mtProperties;

    public String encrypt(String plaintext) {
        if (StringUtils.isBlank(plaintext)) {
            throw new IllegalArgumentException("IM_MESSAGE_PLAINTEXT_EMPTY");
        }
        byte[] secret = resolveSecretBytes();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secret, "AES"), new IvParameterSpec(secret));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("IM_MESSAGE_ENCRYPT_FAILED", e);
        }
    }

    public String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(content).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("IM_MESSAGE_HASH_FAILED", e);
        }
    }

    byte[] resolveSecretBytes() {
        String secret = mtProperties.getComment().getImFollowUp().getImSecret();
        byte[] bytes = StringUtils.defaultString(secret).getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16) {
            throw new IllegalStateException("MT_IM_SECRET_MUST_BE_16_UTF8_BYTES");
        }
        return bytes;
    }
}
