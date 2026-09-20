package com.vaultpass.service;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.security.TwoFactorEnableResponse;
import com.vaultpass.dto.security.TwoFactorSetupResponse;
import com.vaultpass.dto.security.TwoFactorVerifyRequest;
import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.TwoFactorAuth;
import com.vaultpass.entity.TwoFactorRecoveryCode;
import com.vaultpass.entity.User;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.exception.InvalidTotpCodeException;
import com.vaultpass.exception.ResourceNotFoundException;
import com.vaultpass.repository.TwoFactorAuthRepository;
import com.vaultpass.repository.TwoFactorRecoveryCodeRepository;
import com.vaultpass.repository.UserRepository;
import com.vaultpass.security.JwtService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOTP secrets and recovery codes never leave this service in a recoverable
 * form after setup: the secret is AES-256-GCM-encrypted at rest (same
 * EncryptionService as vault passwords) and recovery codes are stored only
 * as SHA-256 hashes.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private static final int RECOVERY_CODE_COUNT = 10;

    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final TwoFactorRecoveryCodeRepository recoveryCodeRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserSessionService userSessionService;
    private final AuditService auditService;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    private final SecureRandom secureRandom = new SecureRandom();

    public boolean isEnabledForUser(UUID userId) {
        return twoFactorAuthRepository.findByUserId(userId).map(TwoFactorAuth::isEnabled).orElse(false);
    }

    @Transactional
    public TwoFactorSetupResponse setup(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String secret = secretGenerator.generate();

        TwoFactorAuth entity = twoFactorAuthRepository.findByUserId(userId)
                .orElseGet(() -> TwoFactorAuth.builder().userId(userId).build());
        entity.setSecretEncrypted(encryptionService.encrypt(secret));
        entity.setEnabled(false);
        entity.setConfirmedAt(null);
        twoFactorAuthRepository.save(entity);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("VaultPass")
                .digits(6)
                .period(30)
                .build();

        String qrImageBase64;
        try {
            qrImageBase64 = Base64.getEncoder().encodeToString(qrGenerator.generate(qrData));
        } catch (QrGenerationException ex) {
            throw new IllegalStateException("Failed to generate 2FA QR code", ex);
        }

        return new TwoFactorSetupResponse(secret, qrImageBase64, qrData.getUri());
    }

    @Transactional
    public TwoFactorEnableResponse enable(UUID userId, String code) {
        TwoFactorAuth entity = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("2FA setup was not started"));

        String secret = encryptionService.decrypt(entity.getSecretEncrypted());
        if (!codeVerifier.isValidCode(secret, code)) {
            throw new InvalidTotpCodeException("Invalid verification code");
        }

        entity.setEnabled(true);
        entity.setConfirmedAt(Instant.now());
        twoFactorAuthRepository.save(entity);

        recoveryCodeRepository.deleteAllByTwoFactorAuthId(entity.getId());
        List<String> rawCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<TwoFactorRecoveryCode> toSave = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = generateRecoveryCode();
            rawCodes.add(raw);
            toSave.add(TwoFactorRecoveryCode.builder()
                    .twoFactorAuthId(entity.getId())
                    .codeHash(sha256(raw))
                    .build());
        }
        recoveryCodeRepository.saveAll(toSave);

        return new TwoFactorEnableResponse(rawCodes);
    }

    @Transactional
    public void disable(UUID userId, String password) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid password");
        }
        twoFactorAuthRepository.findByUserId(userId).ifPresent(entity -> {
            recoveryCodeRepository.deleteAllByTwoFactorAuthId(entity.getId());
            twoFactorAuthRepository.delete(entity);
        });
    }

    // noRollbackFor: invalid-code attempts must still count against the
    // caller's own retry/lockout tracking at the HTTP layer; nothing is
    // written on the failure path here, but keeping the annotation
    // consistent with the other auth flows avoids surprises if that changes.
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, InvalidTotpCodeException.class})
    public AuthResponse verify(TwoFactorVerifyRequest request, String ip, String userAgent) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(request.challengeToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Invalid or expired challenge");
        }
        if (!jwtService.isChallengeToken(claims)) {
            throw new InvalidCredentialsException("Invalid or expired challenge");
        }

        UUID userId = jwtService.extractUserId(claims);
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired challenge"));
        TwoFactorAuth entity = twoFactorAuthRepository.findByUserId(userId)
                .filter(TwoFactorAuth::isEnabled)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired challenge"));

        String secret = encryptionService.decrypt(entity.getSecretEncrypted());
        boolean valid = codeVerifier.isValidCode(secret, request.code()) || consumeRecoveryCode(entity.getId(), request.code());

        if (!valid) {
            auditService.record(AuditEventType.LOGIN_FAILED, userId, ip, userAgent, "Invalid 2FA code");
            throw new InvalidTotpCodeException("Invalid verification code");
        }

        auditService.record(AuditEventType.LOGIN_SUCCESS, userId, ip, userAgent, "Login successful (2FA)");
        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(userId, ip, userAgent);
        userSessionService.record(userId, issued.tokenId(), ip, userAgent);

        return new AuthResponse(accessToken, issued.rawToken(), "Bearer", jwtService.getAccessTokenExpirationSeconds());
    }

    private boolean consumeRecoveryCode(UUID twoFactorAuthId, String code) {
        String hash = sha256(code);
        return recoveryCodeRepository.findByTwoFactorAuthIdAndCodeHashAndUsedAtIsNull(twoFactorAuthId, hash)
                .map(recoveryCode -> {
                    recoveryCode.setUsedAt(Instant.now());
                    recoveryCodeRepository.save(recoveryCode);
                    return true;
                })
                .orElse(false);
    }

    private String generateRecoveryCode() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
