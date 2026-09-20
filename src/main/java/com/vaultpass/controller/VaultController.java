package com.vaultpass.controller;

import com.vaultpass.dto.credential.CredentialCreateRequest;
import com.vaultpass.dto.credential.CredentialPasswordResponse;
import com.vaultpass.dto.credential.CredentialResponse;
import com.vaultpass.dto.credential.CredentialUpdateRequest;
import com.vaultpass.security.CustomUserPrincipal;
import com.vaultpass.service.VaultService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;

    @PostMapping
    public ResponseEntity<CredentialResponse> create(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                       @Valid @RequestBody CredentialCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vaultService.create(principal.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<PagedModel<CredentialResponse>> list(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                 @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(vaultService.list(principal.getUserId(), pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CredentialResponse> getById(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                        @PathVariable UUID id) {
        return ResponseEntity.ok(vaultService.getById(principal.getUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CredentialResponse> update(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody CredentialUpdateRequest request) {
        return ResponseEntity.ok(vaultService.update(principal.getUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable UUID id) {
        vaultService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/password")
    public ResponseEntity<CredentialPasswordResponse> revealPassword(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                       @PathVariable UUID id) {
        return ResponseEntity.ok(vaultService.revealPassword(principal.getUserId(), id));
    }
}
