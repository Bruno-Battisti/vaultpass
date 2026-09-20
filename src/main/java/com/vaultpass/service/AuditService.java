package com.vaultpass.service;

import com.vaultpass.dto.mapper.AuditLogMapper;
import com.vaultpass.dto.security.AuditLogResponse;
import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.AuditLog;
import com.vaultpass.repository.AuditLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit writes are async (at-least-effort, not at-least-once): a failure here
 * is logged but never allowed to break the request that triggered the event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Async
    public void record(AuditEventType eventType, UUID userId, String ipAddress, String userAgent, String description) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .eventType(eventType)
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.error("Failed to persist audit event {}", eventType, ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getActivity(UUID userId, Pageable pageable) {
        return auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable).map(auditLogMapper::toResponse);
    }
}
