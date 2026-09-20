package com.vaultpass.dto.category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        boolean isDefault,
        Instant createdAt
) {
}
