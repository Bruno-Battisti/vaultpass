package com.vaultpass.dto.security;

import com.vaultpass.entity.AuditEventType;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        AuditEventType eventType,
        String description,
        String ipAddress,
        Instant createdAt
) {
}
