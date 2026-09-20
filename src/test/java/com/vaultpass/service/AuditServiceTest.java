package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaultpass.dto.mapper.AuditLogMapper;
import com.vaultpass.dto.security.AuditLogResponse;
import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.AuditLog;
import com.vaultpass.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditService auditService;

    @Test
    void record_savesAuditLogWithGivenFields() {
        UUID userId = UUID.randomUUID();

        auditService.record(AuditEventType.LOGIN_SUCCESS, userId, "203.0.113.5", "junit-agent", "desc");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.5");
        assertThat(saved.getUserAgent()).isEqualTo("junit-agent");
        assertThat(saved.getDescription()).isEqualTo("desc");
    }

    @Test
    void record_repositoryFailure_isSwallowed() {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() ->
                auditService.record(AuditEventType.LOGIN_FAILED, null, "203.0.113.5", "junit-agent", "desc"));
    }

    @Test
    void getActivity_returnsMappedPage() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        AuditLog entry = AuditLog.builder().id(UUID.randomUUID()).userId(userId).eventType(AuditEventType.LOGOUT).build();
        Page<AuditLog> page = new PageImpl<>(List.of(entry));
        when(auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);
        AuditLogResponse mapped = new AuditLogResponse(entry.getId(), AuditEventType.LOGOUT, null, null, Instant.now());
        when(auditLogMapper.toResponse(entry)).thenReturn(mapped);

        Page<AuditLogResponse> result = auditService.getActivity(userId, pageable);

        assertThat(result.getContent()).containsExactly(mapped);
    }
}
