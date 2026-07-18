package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import org.junit.jupiter.api.Test;

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
            assertEquals(plaintext, service.decrypt(encrypted));
        }
    }

    @Test
    void decryptsFixedAesCbcBase64Vector() {
        MtImMessageCryptoService service = service(TEST_SECRET);

        assertEquals("用户回调消息",
                service.decrypt("KPN5uYzYbqeazrLxJMECZh8Dk20HStEoXTfAN4Uj09A="));
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
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(" "));
        assertThrows(IllegalStateException.class, () -> service.decrypt("not-base64"));
        assertEquals(service.sha256("消息🙏"), service.sha256("消息🙏"));
        assertEquals(64, service.sha256("消息🙏").length());
    }

    private MtImMessageCryptoService service(String secret) {
        MtProperties properties = new MtProperties();
        properties.getComment().getImFollowUp().setImSecret(secret);
        return new MtImMessageCryptoService(properties);
    }
}
