package com.gogoga.backend.service;

import com.gogoga.backend.config.YouzanProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YouzanMessageDecryptServiceTest {

    private static final String YOUZAN_AES_IV = "0102030405060708";

    @Test
    void shouldDecryptUrlEncodedAesMessage() throws Exception {
        YouzanProperties properties = new YouzanProperties();
        properties.setClientSecret("8c0c629c95c9bcb36269634788f504fc");
        YouzanMessageDecryptService service = new YouzanMessageDecryptService(properties);
        String payload = "{\"type\":\"APP_SUBSCRIBE\",\"orderNo\":\"E001\",\"shopName\":\"测试店\"}";
        String encrypted = encrypt(payload, properties.getClientSecret(), YOUZAN_AES_IV);
        String encoded = URLEncoder.encode(encrypted, "GBK");

        String result = service.decrypt(encoded);

        assertEquals(payload, result);
    }

    @Test
    void shouldDecryptDoubleUrlEncodedAesMessage() throws Exception {
        YouzanProperties properties = new YouzanProperties();
        properties.setClientSecret("8c0c629c95c9bcb36269634788f504fc");
        YouzanMessageDecryptService service = new YouzanMessageDecryptService(properties);
        String payload = "{\"type\":\"APP_AUTH\",\"orderNo\":\"E002\",\"shopName\":\"测试店\"}";
        String encrypted = encrypt(payload, properties.getClientSecret(), YOUZAN_AES_IV);
        String encoded = URLEncoder.encode(URLEncoder.encode(encrypted, "GBK"), "GBK");

        String result = service.decrypt(encoded);

        assertEquals(payload, result);
    }

    @Test
    void shouldDecryptRawBase64AesMessage() throws Exception {
        YouzanProperties properties = new YouzanProperties();
        properties.setClientSecret("8c0c629c95c9bcb36269634788f504fc");
        YouzanMessageDecryptService service = new YouzanMessageDecryptService(properties);
        String payload = "{\"type\":\"APP_AUTH\",\"orderNo\":\"E003\"}";
        String encrypted = encrypt(payload, properties.getClientSecret(), YOUZAN_AES_IV);

        String result = service.decrypt(encrypted);

        assertEquals(payload, result);
    }

    @Test
    void shouldDecryptProductionMessage() {
        YouzanProperties properties = new YouzanProperties();
        properties.setClientSecret("c367228ef9d0f1cb43104dc2ea6510de");
        YouzanMessageDecryptService service = new YouzanMessageDecryptService(properties);

        String rawMessage = "Ju604aU0SF2YYLX3106bCE87kB1cDChkOLrbCpBXZtilRD16mOCsQH9ZVU%252Fblsys11FBrqxCFIViTPSN8MO5EpgdxLXYedpNnrUm51mg8j%252Be2d2%252F6FL4nHGGz2jFX%252FwJr8DYPVE0cyllWoW%252B7KlowluFdz4qZb5X1aQEgk%252FXawVjyujc6h5b2K53OwLkpdS%252FRtW%252Bw%252FMkfJ%252F%252B6tqf9%252B6qFWPfuznBe4RCMVo183CpoujBCbDoGvrLLm5Jxenk7hs4KA3smOER6sgTJUir72%252Fwhria0O3hJdFzVyDkPVp6S3YeTDRHE28RjuA%252FYcHNLmtgnkUspPzAJ%252B6p%252BLYJoXecFQ7Hgv4PxjPrSC2My3KgATD1J8AeGvegUiDtEOndjhtxp8CKdBJZV8lxQAbiSFmFyBKuGgE1zzujrdvIWmLHm%252BqDs6ENzyK6OHxNTgZInTw7sgG3He7MLe1M5rYAqv%252FMiSvqq%252Bz%252BJpEGZvseYfF6S%252BI%253D";
        String result = service.decrypt(rawMessage);

        System.out.println(result);
    }


    @Test
    void shouldRejectInvalidCiphertext() {
        YouzanProperties properties = new YouzanProperties();
        properties.setClientSecret("secret");
        YouzanMessageDecryptService service = new YouzanMessageDecryptService(properties);

        assertThrows(IllegalArgumentException.class, () -> service.decrypt("not-base64"));
    }

    private String encrypt(String payload, String secret, String iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(buildKey(secret), "AES"),
                new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8))
        );
        return Base64.getEncoder().encodeToString(cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] buildKey(String secret) {
        byte[] source = secret.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        for (int i = 0; i < source.length && i < key.length; i++) {
            key[i] = source[i];
        }
        return key;
    }
}
