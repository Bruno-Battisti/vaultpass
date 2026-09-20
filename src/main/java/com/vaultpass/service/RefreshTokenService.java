package com.vaultpass.service;

import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.RefreshToken;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.repository.RefreshTokenRepository;
import com.vaultpass.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens are opaque, single-use, and rotated on every refresh. The
 * raw value is only ever handed to the client; the database stores just its
 * SHA-256 hash, so a leaked database dump alone can't be replayed. Presenting
 * a token that was already rotated (i.e. already revoked) is treated as
 * evidence of theft: the whole session family for that user is killed.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public record RotationResult(UUID userId, String rawToken) {
    }

    @Transactional
    public String issue(UUID userId, String ip, String userAgent) {
        String rawToken = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(Duration.ofDays(jwtProperties.refreshTokenExpirationDays())))
                .createdByIp(ip)
                .userAgent(userAgent)
                .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    // noRollbackFor is required: the reuse-detection branch revokes every
    // active token for the user and then deliberately throws to signal 401 —
    // without this, Spring would roll back that revocation along with the
    // exception, defeating the whole point of killing the session family.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public RotationResult rotate(String rawToken, String ip, String userAgent) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        if (existing.isRevoked()) {
            auditService.record(AuditEventType.TOKEN_REUSE_DETECTED, existing.getUserId(), ip, userAgent,
                    "Reuse of a revoked refresh token detected; all sessions revoked");
            revokeAllForUser(existing.getUserId());
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        String newRawToken = generateRawToken();
        RefreshToken replacement = RefreshToken.builder()
                .userId(existing.getUserId())
                .tokenHash(hash(newRawToken))
                .expiresAt(Instant.now().plus(Duration.ofDays(jwtProperties.refreshTokenExpirationDays())))
                .createdByIp(ip)
                .userAgent(userAgent)
                .build();
        refreshTokenRepository.save(replacement);

        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
        existing.setReplacedByTokenId(replacement.getId());
        refreshTokenRepository.save(existing);

        return new RotationResult(existing.getUserId(), newRawToken);
    }

    @Transactional
    public Optional<UUID> revoke(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken)).map(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            return token.getUserId();
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId);
        Instant now = Instant.now();
        active.forEach(token -> {
            token.setRevoked(true);
            token.setRevokedAt(now);
        });
        refreshTokenRepository.saveAll(active);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
