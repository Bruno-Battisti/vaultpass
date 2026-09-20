package com.vaultpass.controller;

import com.vaultpass.dto.security.AuditLogResponse;
import com.vaultpass.security.CustomUserPrincipal;
import com.vaultpass.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private final AuditService auditService;

    @GetMapping("/activity")
    public ResponseEntity<PagedModel<AuditLogResponse>> activity(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                    @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(auditService.getActivity(principal.getUserId(), pageable)));
    }
}
