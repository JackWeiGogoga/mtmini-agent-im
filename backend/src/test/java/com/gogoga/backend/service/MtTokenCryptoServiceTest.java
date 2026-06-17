package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtTokenCryptoServiceTest {

    @Test
    void shouldEncryptDecryptAndFingerprintToken() {
        MtProperties properties = new MtProperties();
        properties.getToken().getHistory().setEncryptionKey("unit-test-encryption-key");
        properties.getToken().getHistory().setFingerprintKey("unit-test-fingerprint-key");
        MtTokenCryptoService service = new MtTokenCryptoService(properties);

        String cipherText = service.encrypt("access-token-secret");

        assertTrue(cipherText.startsWith("v1:"));
        assertFalse(cipherText.contains("access-token-secret"));
        assertEquals("access-token-secret", service.decrypt(cipherText));
        assertEquals(service.fingerprint("access-token-secret"), service.fingerprint("access-token-secret"));
        assertNotEquals(service.fingerprint("access-token-secret"), service.fingerprint("other-token"));
    }
}
