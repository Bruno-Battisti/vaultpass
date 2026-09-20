package com.vaultpass.service;

import com.vaultpass.entity.AuditEventType;
import com.vaultpass.repository.AuditLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately cheap: no geo-IP lookups or external services, just "have we
 * ever seen this IP succeed a login for this user before". Good enough to
 * flag a genuinely new device/location without adding infrastructure.
 */
@Service
@RequiredArgsConstructor
public class SuspiciousActivityDetector {

    private static final int HISTORY_SIZE = 20;

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public void checkAndRecord(UUID userId, String ip, String userAgent) {
        if (ip == null) {
            return;
        }
        boolean seenBefore = auditLogRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, HISTORY_SIZE))
                .stream()
                .anyMatch(entry -> entry.getEventType() == AuditEventType.LOGIN_SUCCESS && ip.equals(entry.getIpAddress()));

        if (!seenBefore) {
            auditService.record(AuditEventType.LOGIN_NEW_DEVICE, userId, ip, userAgent,
                    "Login from a new IP address not seen in recent history");
        }
    }
}
