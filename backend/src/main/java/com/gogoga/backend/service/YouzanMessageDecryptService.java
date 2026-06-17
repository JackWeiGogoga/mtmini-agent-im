package com.gogoga.backend.service;

import com.gogoga.backend.config.YouzanProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 有赞回调 message 解密服务
 */
@Service
@RequiredArgsConstructor
public class YouzanMessageDecryptService {

    private static final String AES_IV = "0102030405060708";
    private static final int MAX_URL_DECODE_ROUNDS = 2;

    private final YouzanProperties properties;

    public String decrypt(String rawMessage) {
        if (StringUtils.isBlank(rawMessage)) {
            throw new IllegalArgumentException("message must not be blank");
        }
        String secret = properties.getClientSecret();
        if (StringUtils.isBlank(secret)) {
            throw new IllegalStateException("Youzan client secret is not configured");
        }
        try {
            Charset urlCharset = Charset.forName(StringUtils.defaultIfBlank(
                    properties.getCallback().getUrlCharset(),
                    "GBK"
            ));
            Exception firstFailure = null;
            for (String candidate : buildCiphertextCandidates(rawMessage, urlCharset)) {
                try {
                    return decryptCandidate(candidate, secret);
                } catch (Exception e) {
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
            }
            throw firstFailure == null
                    ? new IllegalArgumentException("message must not be blank")
                    : firstFailure;
        } catch (Exception e) {
            throw new IllegalArgumentException("decrypt Youzan message failed", e);
        }
    }

    private Set<String> buildCiphertextCandidates(String rawMessage, Charset urlCharset) {
        Set<String> candidates = new LinkedHashSet<>();
        String current = rawMessage;
        for (int i = 0; i <= MAX_URL_DECODE_ROUNDS; i++) {
            if (StringUtils.isNotBlank(current)) {
                candidates.add(current);
            }
            if (i == MAX_URL_DECODE_ROUNDS) {
                break;
            }
            try {
                String decoded = URLDecoder.decode(current, urlCharset);
                if (StringUtils.equals(decoded, current)) {
                    break;
                }
                current = decoded;
            } catch (IllegalArgumentException e) {
                break;
            }
        }
        return candidates;
    }

    private String decryptCandidate(String ciphertext, String secret) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                buildSecretKey(secret),
                new IvParameterSpec(AES_IV.getBytes(StandardCharsets.UTF_8))
        );
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKeySpec buildSecretKey(String secret) {
        byte[] source = secret.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        for (int i = 0; i < source.length && i < key.length; i++) {
            key[i] = source[i];
        }
        return new SecretKeySpec(key, "AES");
    }
}
