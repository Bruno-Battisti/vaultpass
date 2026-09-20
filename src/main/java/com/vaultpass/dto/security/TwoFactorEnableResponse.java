package com.vaultpass.dto.security;

import java.util.List;

/**
 * The 10 recovery codes are returned here in plaintext exactly once — only
 * their SHA-256 hash is persisted, so this is the only chance the user gets
 * to save them.
 */
public record TwoFactorEnableResponse(
        List<String> recoveryCodes
) {
}
