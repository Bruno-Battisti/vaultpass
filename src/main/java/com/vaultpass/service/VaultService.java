package com.vaultpass.service;

import com.vaultpass.dto.credential.CredentialCreateRequest;
import com.vaultpass.dto.credential.CredentialPasswordResponse;
import com.vaultpass.dto.credential.CredentialResponse;
import com.vaultpass.dto.credential.CredentialUpdateRequest;
import com.vaultpass.dto.mapper.CredentialMapper;
import com.vaultpass.entity.Credential;
import com.vaultpass.exception.ResourceNotFoundException;
import com.vaultpass.repository.CredentialRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every lookup goes through findByIdAndUserId and returns 404 (never 403)
 * for both "doesn't exist" and "not yours" — a 403 would confirm the id
 * exists to an attacker probing other users' resources (OWASP IDOR).
 */
@Service
@RequiredArgsConstructor
public class VaultService {

    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final CredentialMapper credentialMapper;

    @Transactional
    public CredentialResponse create(UUID userId, CredentialCreateRequest request) {
        Credential credential = Credential.builder()
                .userId(userId)
                .title(request.title())
                .username(request.username())
                .encryptedPassword(encryptionService.encrypt(request.password()))
                .url(request.url())
                .notes(request.notes())
                .build();
        return credentialMapper.toResponse(credentialRepository.save(credential));
    }

    @Transactional(readOnly = true)
    public Page<CredentialResponse> list(UUID userId, Pageable pageable) {
        return credentialRepository.findAllByUserId(userId, pageable).map(credentialMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CredentialResponse getById(UUID userId, UUID id) {
        return credentialMapper.toResponse(findOwnedOrThrow(id, userId));
    }

    @Transactional
    public CredentialResponse update(UUID userId, UUID id, CredentialUpdateRequest request) {
        Credential credential = findOwnedOrThrow(id, userId);
        credential.setTitle(request.title());
        credential.setUsername(request.username());
        credential.setUrl(request.url());
        credential.setNotes(request.notes());
        if (request.password() != null && !request.password().isBlank()) {
            credential.setEncryptedPassword(encryptionService.encrypt(request.password()));
        }
        return credentialMapper.toResponse(credentialRepository.save(credential));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        credentialRepository.delete(findOwnedOrThrow(id, userId));
    }

    @Transactional(readOnly = true)
    public CredentialPasswordResponse revealPassword(UUID userId, UUID id) {
        Credential credential = findOwnedOrThrow(id, userId);
        return new CredentialPasswordResponse(encryptionService.decrypt(credential.getEncryptedPassword()));
    }

    private Credential findOwnedOrThrow(UUID id, UUID userId) {
        return credentialRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));
    }
}
