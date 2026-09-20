package com.vaultpass.dto.credential;

import java.time.Instant;
import java.util.UUID;

/**
 * Never carries the plaintext or encrypted password. The actual secret is
 * only revealed through the dedicated GET /vault/{id}/password endpoint.
 */
public record CredentialResponse(
        UUID id,
        String title,
        String username,
        String url,
        String notes,
        UUID categoryId,
        boolean hasPassword,
        Instant createdAt,
        Instant updatedAt
) {
}
