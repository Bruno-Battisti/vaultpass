package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.RefreshToken;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.repository.RefreshTokenRepository;
import com.vaultpass.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.refreshTokenExpirationDays()).thenReturn(7L);
    }

    private static String sha256Base64(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void issue_persistsOnlyTheHashOfTheRawToken() {
        UUID userId = UUID.randomUUID();
        UUID generatedId = UUID.randomUUID();
        // Simulates Hibernate's client-side UUID generation, which assigns the
        // id synchronously on save() — a plain mock wouldn't do that itself.
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            token.setId(generatedId);
            return token;
        });

        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(userId, "203.0.113.5", "junit");
        String rawToken = issued.rawToken();

        assertThat(rawToken).isNotBlank();
        assertThat(issued.tokenId()).isEqualTo(generatedId);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(rawToken).isEqualTo(sha256Base64(rawToken));
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void rotate_validToken_revokesOldAndIssuesNew() {
        String rawToken = "existing-raw-token";
        UUID userId = UUID.randomUUID();
        RefreshToken existing = RefreshToken.builder().id(UUID.randomUUID()).userId(userId)
                .tokenHash(sha256Base64(rawToken)).revoked(false).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(refreshTokenRepository.findByTokenHash(sha256Base64(rawToken))).thenReturn(Optional.of(existing));
        // Simulates Hibernate's client-side UUID generation, which assigns the
        // id synchronously on save() — a plain mock wouldn't do that itself.
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
            }
            return token;
        });

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawToken, "203.0.113.5", "junit");

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.rawToken()).isNotBlank().isNotEqualTo(rawToken);
        assertThat(existing.isRevoked()).isTrue();
        assertThat(existing.getReplacedByTokenId()).isNotNull();
    }

    @Test
    void rotate_alreadyRevokedToken_revokesWholeFamilyAndThrows() {
        String rawToken = "stolen-raw-token";
        UUID userId = UUID.randomUUID();
        RefreshToken existing = RefreshToken.builder().id(UUID.randomUUID()).userId(userId)
                .tokenHash(sha256Base64(rawToken)).revoked(true).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(refreshTokenRepository.findByTokenHash(sha256Base64(rawToken))).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId)).thenReturn(List.of());

        assertThrows(InvalidCredentialsException.class, () -> refreshTokenService.rotate(rawToken, "203.0.113.5", "junit"));

        verify(auditService).record(eq(AuditEventType.TOKEN_REUSE_DETECTED), eq(userId), anyString(), anyString(), anyString());
        verify(refreshTokenRepository).findAllByUserIdAndRevokedFalse(userId);
    }

    @Test
    void rotate_expiredToken_throws() {
        String rawToken = "expired-raw-token";
        RefreshToken existing = RefreshToken.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .tokenHash(sha256Base64(rawToken)).revoked(false).expiresAt(Instant.now().minusSeconds(10)).build();
        when(refreshTokenRepository.findByTokenHash(sha256Base64(rawToken))).thenReturn(Optional.of(existing));

        assertThrows(InvalidCredentialsException.class, () -> refreshTokenService.rotate(rawToken, "203.0.113.5", "junit"));
    }

    @Test
    void rotate_unknownToken_throws() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> refreshTokenService.rotate("unknown", "203.0.113.5", "junit"));
    }

    @Test
    void revoke_existingToken_marksRevokedAndReturnsUserId() {
        String rawToken = "raw-token";
        UUID userId = UUID.randomUUID();
        RefreshToken existing = RefreshToken.builder().id(UUID.randomUUID()).userId(userId)
                .tokenHash(sha256Base64(rawToken)).revoked(false).build();
        when(refreshTokenRepository.findByTokenHash(sha256Base64(rawToken))).thenReturn(Optional.of(existing));

        Optional<UUID> result = refreshTokenService.revoke(rawToken);

        assertThat(result).contains(userId);
        assertThat(existing.isRevoked()).isTrue();
    }

    @Test
    void revoke_unknownToken_returnsEmpty() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThat(refreshTokenService.revoke("unknown")).isEmpty();
    }
}
