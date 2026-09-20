package com.vaultpass.dto.auth;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String name,
        String email,
        Instant createdAt
) {
}
