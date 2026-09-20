package com.vaultpass.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptionConfig {

    private static final int AES_256_KEY_LENGTH_BYTES = 32;

    @Bean
    public SecretKey masterEncryptionKey(@Value("${security.encryption.master-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != AES_256_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "MASTER_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256); got " + keyBytes.length + " bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
