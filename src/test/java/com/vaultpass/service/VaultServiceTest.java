package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaultpass.dto.credential.CredentialCreateRequest;
import com.vaultpass.dto.credential.CredentialPasswordResponse;
import com.vaultpass.dto.credential.CredentialResponse;
import com.vaultpass.dto.credential.CredentialUpdateRequest;
import com.vaultpass.dto.mapper.CredentialMapper;
import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.Category;
import com.vaultpass.entity.Credential;
import com.vaultpass.exception.ResourceNotFoundException;
import com.vaultpass.repository.CategoryRepository;
import com.vaultpass.repository.CredentialRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VaultServiceTest {

    private static final String IP = "203.0.113.5";
    private static final String USER_AGENT = "junit-agent";

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private CredentialMapper credentialMapper;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private VaultService vaultService;

    @Test
    void create_encryptsPasswordBeforeSaving() {
        UUID userId = UUID.randomUUID();
        CredentialCreateRequest request = new CredentialCreateRequest("GitHub", "bruno", "s3cr3t", "https://github.com", null, null);
        when(encryptionService.encrypt("s3cr3t")).thenReturn("cipher-text");

        Credential saved = Credential.builder().id(UUID.randomUUID()).userId(userId).title("GitHub")
                .encryptedPassword("cipher-text").build();
        when(credentialRepository.save(any(Credential.class))).thenReturn(saved);

        CredentialResponse expected = new CredentialResponse(saved.getId(), "GitHub", "bruno", "https://github.com",
                null, null, true, Instant.now(), Instant.now());
        when(credentialMapper.toResponse(saved)).thenReturn(expected);

        CredentialResponse result = vaultService.create(userId, request, IP, USER_AGENT);

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedPassword()).isEqualTo("cipher-text");
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        verify(auditService).record(eq(AuditEventType.PASSWORD_CREATED), eq(userId), eq(IP), eq(USER_AGENT), anyString());
    }

    @Test
    void getById_notOwnedByUser_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndUserId(credentialId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> vaultService.getById(userId, credentialId));
    }

    @Test
    void revealPassword_decryptsStoredValueAndRecordsAudit() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Credential credential = Credential.builder().id(credentialId).userId(userId).title("GitHub")
                .encryptedPassword("cipher-text").build();
        when(credentialRepository.findByIdAndUserId(credentialId, userId)).thenReturn(Optional.of(credential));
        when(encryptionService.decrypt("cipher-text")).thenReturn("s3cr3t");

        CredentialPasswordResponse response = vaultService.revealPassword(userId, credentialId, IP, USER_AGENT);

        assertThat(response.password()).isEqualTo("s3cr3t");
        verify(auditService).record(eq(AuditEventType.PASSWORD_VIEWED), eq(userId), eq(IP), eq(USER_AGENT), anyString());
    }

    @Test
    void update_withNullPassword_keepsExistingEncryptedPassword() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Credential existing = Credential.builder().id(credentialId).userId(userId).title("Old")
                .encryptedPassword("old-cipher").build();
        CredentialUpdateRequest request = new CredentialUpdateRequest("New Title", "bruno", null, null, null, null);
        when(credentialRepository.findByIdAndUserId(credentialId, userId)).thenReturn(Optional.of(existing));
        when(credentialRepository.save(any(Credential.class))).thenAnswer(inv -> inv.getArgument(0));
        CredentialResponse expected = new CredentialResponse(credentialId, "New Title", "bruno", null, null, null,
                true, Instant.now(), Instant.now());
        when(credentialMapper.toResponse(any(Credential.class))).thenReturn(expected);

        vaultService.update(userId, credentialId, request, IP, USER_AGENT);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedPassword()).isEqualTo("old-cipher");
        verify(encryptionService, never()).encrypt(anyString());
        verify(auditService).record(eq(AuditEventType.PASSWORD_UPDATED), eq(userId), eq(IP), eq(USER_AGENT), anyString());
    }

    @Test
    void update_withNewPassword_reEncrypts() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Credential existing = Credential.builder().id(credentialId).userId(userId).title("Old")
                .encryptedPassword("old-cipher").build();
        CredentialUpdateRequest request = new CredentialUpdateRequest("Old", null, "new-password", null, null, null);
        when(credentialRepository.findByIdAndUserId(credentialId, userId)).thenReturn(Optional.of(existing));
        when(encryptionService.encrypt("new-password")).thenReturn("new-cipher");
        when(credentialRepository.save(any(Credential.class))).thenAnswer(inv -> inv.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class))).thenReturn(
                new CredentialResponse(credentialId, "Old", null, null, null, null, true, Instant.now(), Instant.now()));

        vaultService.update(userId, credentialId, request, IP, USER_AGENT);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedPassword()).isEqualTo("new-cipher");
    }

    @Test
    void delete_removesOwnedCredentialAndRecordsAudit() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Credential existing = Credential.builder().id(credentialId).userId(userId).title("GitHub").build();
        when(credentialRepository.findByIdAndUserId(credentialId, userId)).thenReturn(Optional.of(existing));

        vaultService.delete(userId, credentialId, IP, USER_AGENT);

        verify(credentialRepository).delete(existing);
        verify(auditService).record(eq(AuditEventType.PASSWORD_DELETED), eq(userId), eq(IP), eq(USER_AGENT), anyString());
    }

    @Test
    void delete_notOwnedByUser_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        when(credentialRepository.findByIdAndUserId(credentialId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> vaultService.delete(userId, credentialId, IP, USER_AGENT));
        verify(credentialRepository, never()).delete(any());
    }

    @Test
    void create_withCategoryNotOwnedByUser_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID foreignCategoryId = UUID.randomUUID();
        CredentialCreateRequest request =
                new CredentialCreateRequest("GitHub", "bruno", "s3cr3t", null, null, foreignCategoryId);
        when(categoryRepository.findByIdAndUserId(foreignCategoryId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> vaultService.create(userId, request, IP, USER_AGENT));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void create_withOwnedCategory_associatesCredentialToCategory() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CredentialCreateRequest request =
                new CredentialCreateRequest("GitHub", "bruno", "s3cr3t", null, null, categoryId);
        when(categoryRepository.findByIdAndUserId(categoryId, userId))
                .thenReturn(Optional.of(Category.builder().id(categoryId).userId(userId).build()));
        when(encryptionService.encrypt("s3cr3t")).thenReturn("cipher-text");
        when(credentialRepository.save(any(Credential.class))).thenAnswer(inv -> inv.getArgument(0));
        when(credentialMapper.toResponse(any(Credential.class))).thenReturn(
                new CredentialResponse(UUID.randomUUID(), "GitHub", "bruno", null, null, categoryId, true,
                        Instant.now(), Instant.now()));

        vaultService.create(userId, request, IP, USER_AGENT);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isEqualTo(categoryId);
    }
}
