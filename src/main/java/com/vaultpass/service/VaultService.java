package com.vaultpass.service;

import com.vaultpass.dto.credential.CredentialCreateRequest;
import com.vaultpass.dto.credential.CredentialPasswordResponse;
import com.vaultpass.dto.credential.CredentialResponse;
import com.vaultpass.dto.credential.CredentialUpdateRequest;
import com.vaultpass.dto.mapper.CredentialMapper;
import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.Credential;
import com.vaultpass.exception.ResourceNotFoundException;
import com.vaultpass.repository.CategoryRepository;
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
 * exists to an attacker probing other users' resources (OWASP IDOR). The
 * same rule applies to categoryId: assigning a credential to a category
 * you don't own is treated as "category not found", not silently accepted.
 */
@Service
@RequiredArgsConstructor
public class VaultService {

    private final CredentialRepository credentialRepository;
    private final CategoryRepository categoryRepository;
    private final EncryptionService encryptionService;
    private final CredentialMapper credentialMapper;
    private final AuditService auditService;

    @Transactional
    public CredentialResponse create(UUID userId, CredentialCreateRequest request, String ip, String userAgent) {
        validateCategoryOwnership(userId, request.categoryId());

        Credential credential = Credential.builder()
                .userId(userId)
                .categoryId(request.categoryId())
                .title(request.title())
                .username(request.username())
                .encryptedPassword(encryptionService.encrypt(request.password()))
                .url(request.url())
                .notes(request.notes())
                .build();
        Credential saved = credentialRepository.save(credential);
        auditService.record(AuditEventType.PASSWORD_CREATED, userId, ip, userAgent, "Credential '" + saved.getTitle() + "' created");
        return credentialMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CredentialResponse> list(UUID userId, String search, UUID categoryId, Pageable pageable) {
        return credentialRepository.search(userId, categoryId, search, pageable).map(credentialMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CredentialResponse getById(UUID userId, UUID id) {
        return credentialMapper.toResponse(findOwnedOrThrow(id, userId));
    }

    @Transactional
    public CredentialResponse update(UUID userId, UUID id, CredentialUpdateRequest request, String ip, String userAgent) {
        validateCategoryOwnership(userId, request.categoryId());

        Credential credential = findOwnedOrThrow(id, userId);
        credential.setTitle(request.title());
        credential.setUsername(request.username());
        credential.setUrl(request.url());
        credential.setNotes(request.notes());
        credential.setCategoryId(request.categoryId());
        if (request.password() != null && !request.password().isBlank()) {
            credential.setEncryptedPassword(encryptionService.encrypt(request.password()));
        }
        Credential saved = credentialRepository.save(credential);
        auditService.record(AuditEventType.PASSWORD_UPDATED, userId, ip, userAgent, "Credential '" + saved.getTitle() + "' updated");
        return credentialMapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID id, String ip, String userAgent) {
        Credential credential = findOwnedOrThrow(id, userId);
        credentialRepository.delete(credential);
        auditService.record(AuditEventType.PASSWORD_DELETED, userId, ip, userAgent, "Credential '" + credential.getTitle() + "' deleted");
    }

    @Transactional(readOnly = true)
    public CredentialPasswordResponse revealPassword(UUID userId, UUID id, String ip, String userAgent) {
        Credential credential = findOwnedOrThrow(id, userId);
        auditService.record(AuditEventType.PASSWORD_VIEWED, userId, ip, userAgent, "Credential '" + credential.getTitle() + "' password viewed");
        return new CredentialPasswordResponse(encryptionService.decrypt(credential.getEncryptedPassword()));
    }

    private Credential findOwnedOrThrow(UUID id, UUID userId) {
        return credentialRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));
    }

    private void validateCategoryOwnership(UUID userId, UUID categoryId) {
        if (categoryId != null && categoryRepository.findByIdAndUserId(categoryId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Category not found");
        }
    }
}
