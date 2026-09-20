package com.vaultpass.dto.security;

import java.time.Instant;
import java.util.UUID;

public record UserSessionResponse(
        UUID id,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant lastUsedAt
) {
}
