package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaultpass.exception.EncryptionOperationException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        encryptionService = new EncryptionService(new SecretKeySpec(keyBytes, "AES"));
    }

    @Test
    void encryptThenDecrypt_returnsOriginalPlainText() {
        String plain = "s3cr3t-P@ssw0rd!";

        String cipherText = encryptionService.encrypt(plain);

        assertThat(cipherText).isNotEqualTo(plain);
        assertThat(encryptionService.decrypt(cipherText)).isEqualTo(plain);
    }

    @Test
    void encrypt_sameInputTwice_producesDifferentCiphertext() {
        String plain = "same-password";

        String first = encryptionService.encrypt(plain);
        String second = encryptionService.encrypt(plain);

        // Proves the IV is randomized per call, not reused.
        assertThat(first).isNotEqualTo(second);
        assertThat(encryptionService.decrypt(first)).isEqualTo(plain);
        assertThat(encryptionService.decrypt(second)).isEqualTo(plain);
    }

    @Test
    void decrypt_withWrongKey_throwsEncryptionOperationException() {
        String cipherText = encryptionService.encrypt("value");

        byte[] otherKeyBytes = new byte[32];
        new SecureRandom().nextBytes(otherKeyBytes);
        EncryptionService otherService = new EncryptionService(new SecretKeySpec(otherKeyBytes, "AES"));

        assertThrows(EncryptionOperationException.class, () -> otherService.decrypt(cipherText));
    }

    @Test
    void decrypt_withTamperedCiphertext_throwsEncryptionOperationException() {
        String cipherText = encryptionService.encrypt("value");
        byte[] decoded = Base64.getDecoder().decode(cipherText);
        decoded[decoded.length - 1] ^= 0x01; // flip a bit -> GCM auth tag check must fail
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertThrows(EncryptionOperationException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    void decrypt_withGarbageInput_throwsEncryptionOperationException() {
        assertThrows(EncryptionOperationException.class, () -> encryptionService.decrypt("not-valid-base64!!"));
    }
}
