package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MtImMessageCryptoServiceTest {

    private static final String TEST_SECRET = "1234567890abcdef";

    @Test
    void encryptsAsciiChineseEmojiAndBlockBoundaryWithConfiguredKeyAndIv() throws Exception {
        MtImMessageCryptoService service = service(TEST_SECRET);

        for (String plaintext : new String[]{"0123456789abcdef", "您好，请提供订单和手机号🙏", "🙂🙂"}) {
            String encrypted = service.encrypt(plaintext);

            assertFalse(encrypted.contains(plaintext));
            assertEquals(plaintext, decrypt(encrypted, TEST_SECRET));
        }
    }

    @Test
    void rejectsSecretWhoseUtf8EncodingIsNotExactlySixteenBytes() {
        assertThrows(IllegalStateException.class, () -> service("short-secret").encrypt("hello"));
        assertThrows(IllegalStateException.class, () -> service("十六个汉字并不是十六字节").encrypt("hello"));
    }

    @Test
    void rejectsBlankPlaintextAndProducesStableHash() {
        MtImMessageCryptoService service = service(TEST_SECRET);

        assertThrows(IllegalArgumentException.class, () -> service.encrypt(" "));
        assertEquals(service.sha256("消息🙏"), service.sha256("消息🙏"));
        assertEquals(64, service.sha256("消息🙏").length());
    }

    private MtImMessageCryptoService service(String secret) {
        MtProperties properties = new MtProperties();
        properties.getComment().getImFollowUp().setImSecret(secret);
        return new MtImMessageCryptoService(properties);
    }

    private String decrypt(String encrypted, String secret) throws Exception {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
        return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
    }
}
