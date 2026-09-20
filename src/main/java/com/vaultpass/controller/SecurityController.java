package com.vaultpass.controller;

import com.vaultpass.dto.security.AuditLogResponse;
import com.vaultpass.dto.security.UserSessionResponse;
import com.vaultpass.security.CustomUserPrincipal;
import com.vaultpass.service.AuditService;
import com.vaultpass.service.UserSessionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private final AuditService auditService;
    private final UserSessionService userSessionService;

    @GetMapping("/activity")
    public ResponseEntity<PagedModel<AuditLogResponse>> activity(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                    @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(auditService.getActivity(principal.getUserId(), pageable)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<UserSessionResponse>> sessions(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(userSessionService.listActive(principal.getUserId()));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable UUID id) {
        userSessionService.revoke(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(@AuthenticationPrincipal CustomUserPrincipal principal) {
        userSessionService.revokeAll(principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
