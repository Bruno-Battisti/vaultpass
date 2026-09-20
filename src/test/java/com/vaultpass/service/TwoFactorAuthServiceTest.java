package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.security.TwoFactorEnableResponse;
import com.vaultpass.dto.security.TwoFactorSetupResponse;
import com.vaultpass.dto.security.TwoFactorVerifyRequest;
import com.vaultpass.entity.TwoFactorAuth;
import com.vaultpass.entity.TwoFactorRecoveryCode;
import com.vaultpass.entity.User;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.exception.InvalidTotpCodeException;
import com.vaultpass.repository.TwoFactorAuthRepository;
import com.vaultpass.repository.TwoFactorRecoveryCodeRepository;
import com.vaultpass.repository.UserRepository;
import com.vaultpass.security.JwtService;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceTest {

    @Mock
    private TwoFactorAuthRepository twoFactorAuthRepository;

    @Mock
    private TwoFactorRecoveryCodeRepository recoveryCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TwoFactorAuthService twoFactorAuthService;

    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();

    private String currentCodeFor(String secret) throws Exception {
        long counter = Math.floorDiv(timeProvider.getTime(), 30);
        return codeGenerator.generate(secret, counter);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void setup_encryptsSecretAndPersistsDisabled() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("bruno@example.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(twoFactorAuthRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted-secret");
        when(twoFactorAuthRepository.save(any(TwoFactorAuth.class))).thenAnswer(inv -> inv.getArgument(0));

        TwoFactorSetupResponse response = twoFactorAuthService.setup(userId);

        assertThat(response.secret()).isNotBlank();
        assertThat(response.otpAuthUrl()).startsWith("otpauth://totp");
        assertThat(response.qrCodeImageBase64()).isNotBlank();

        ArgumentCaptor<TwoFactorAuth> captor = ArgumentCaptor.forClass(TwoFactorAuth.class);
        verify(twoFactorAuthRepository).save(captor.capture());
        assertThat(captor.getValue().getSecretEncrypted()).isEqualTo("encrypted-secret");
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    @Test
    void enable_withValidCode_enablesAndReturnsTenUniqueRecoveryCodes() throws Exception {
        UUID userId = UUID.randomUUID();
        String secret = new DefaultSecretGenerator().generate();
        TwoFactorAuth entity = TwoFactorAuth.builder().id(UUID.randomUUID()).userId(userId)
                .secretEncrypted("encrypted-secret").enabled(false).build();
        when(twoFactorAuthRepository.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn(secret);
        when(twoFactorAuthRepository.save(any(TwoFactorAuth.class))).thenAnswer(inv -> inv.getArgument(0));

        TwoFactorEnableResponse response = twoFactorAuthService.enable(userId, currentCodeFor(secret));

        assertThat(response.recoveryCodes()).hasSize(10);
        assertThat(new HashSet<>(response.recoveryCodes())).hasSize(10);
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.getConfirmedAt()).isNotNull();
        ArgumentCaptor<List<TwoFactorRecoveryCode>> savedCodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).saveAll(savedCodesCaptor.capture());
        assertThat(savedCodesCaptor.getValue()).hasSize(10);
    }

    @Test
    void enable_withInvalidCode_throwsAndStaysDisabled() {
        UUID userId = UUID.randomUUID();
        TwoFactorAuth entity = TwoFactorAuth.builder().id(UUID.randomUUID()).userId(userId)
                .secretEncrypted("encrypted-secret").enabled(false).build();
        when(twoFactorAuthRepository.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn(new DefaultSecretGenerator().generate());

        assertThrows(InvalidTotpCodeException.class, () -> twoFactorAuthService.enable(userId, "000000"));
        assertThat(entity.isEnabled()).isFalse();
        verify(twoFactorAuthRepository, never()).save(any());
    }

    @Test
    void disable_withWrongPassword_throwsAndKeepsSetup() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).passwordHash("hashed").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> twoFactorAuthService.disable(userId, "wrong"));
        verify(twoFactorAuthRepository, never()).delete(any());
    }

    @Test
    void disable_withCorrectPassword_removesSetupAndRecoveryCodes() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).passwordHash("hashed").build();
        TwoFactorAuth entity = TwoFactorAuth.builder().id(UUID.randomUUID()).userId(userId).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(twoFactorAuthRepository.findByUserId(userId)).thenReturn(Optional.of(entity));

        twoFactorAuthService.disable(userId, "correct");

        verify(recoveryCodeRepository).deleteAllByTwoFactorAuthId(entity.getId());
        verify(twoFactorAuthRepository).delete(entity);
    }

    @Test
    void verify_withValidTotpCode_issuesTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        String secret = new DefaultSecretGenerator().generate();
        User user = User.builder().id(userId).enabled(true).build();
        TwoFactorAuth entity = TwoFactorAuth.builder().id(UUID.randomUUID()).userId(userId)
                .secretEncrypted("encrypted-secret").enabled(true).build();
        Claims claims = mock(Claims.class);
        when(jwtService.parseClaims("challenge-token")).thenReturn(claims);
        when(jwtService.isChallengeToken(claims)).thenReturn(true);
        when(jwtService.extractUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(twoFactorAuthRepository.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn(secret);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(userId, "ip", "ua"))
                .thenReturn(new RefreshTokenService.IssuedToken(UUID.randomUUID(), "refresh-token"));

        AuthResponse response = twoFactorAuthService.verify(
                new TwoFactorVerifyRequest("challenge-token", currentCodeFor(secret)), "ip", "ua");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void verify_withValidRecoveryCode_consumesItOnce() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).enabled(true).build();
        TwoFactorAuth entity = TwoFactorAuth.builder().id(UUID.randomUUID()).userId(userId)
                .secretEncrypted("encrypted-secret").enabled(true).build();
        Claims claims = mock(Claims.class);
        when(jwtService.parseClaims("challenge-token")).thenReturn(claims);
        when(jwtService.isChallengeToken(claims)).thenReturn(true);
        when(jwtService.extractUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(twoFactorAuthRepository.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn(new DefaultSecretGenerator().generate());

        String recoveryCode = "ABCDEFGH";
        TwoFactorRecoveryCode stored = TwoFactorRecoveryCode.builder().id(UUID.randomUUID())
                .twoFactorAuthId(entity.getId()).codeHash(sha256(recoveryCode)).build();
        when(recoveryCodeRepository.findByTwoFactorAuthIdAndCodeHashAndUsedAtIsNull(entity.getId(), sha256(recoveryCode)))
                .thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(userId, "ip", "ua"))
                .thenReturn(new RefreshTokenService.IssuedToken(UUID.randomUUID(), "refresh-token"));

        AuthResponse response = twoFactorAuthService.verify(
                new TwoFactorVerifyRequest("challenge-token", recoveryCode), "ip", "ua");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(stored.getUsedAt()).isNotNull();
        verify(recoveryCodeRepository).save(stored);
    }

    @Test
    void verify_withInvalidCodeAndNoRecoveryMatch_throws() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).enabled(true).build();
        TwoFactorAuth entity = TwoFactorAuth.builder().id(UUID.randomUUID()).userId(userId)
                .secretEncrypted("encrypted-secret").enabled(true).build();
        Claims claims = mock(Claims.class);
        when(jwtService.parseClaims("challenge-token")).thenReturn(claims);
        when(jwtService.isChallengeToken(claims)).thenReturn(true);
        when(jwtService.extractUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(twoFactorAuthRepository.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn(new DefaultSecretGenerator().generate());
        when(recoveryCodeRepository.findByTwoFactorAuthIdAndCodeHashAndUsedAtIsNull(any(), anyString()))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTotpCodeException.class,
                () -> twoFactorAuthService.verify(new TwoFactorVerifyRequest("challenge-token", "000000"), "ip", "ua"));
        verify(refreshTokenService, never()).issue(any(), anyString(), anyString());
    }

    @Test
    void verify_withExpiredOrInvalidChallengeToken_throws() {
        when(jwtService.parseClaims("garbage")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad"));

        assertThrows(InvalidCredentialsException.class,
                () -> twoFactorAuthService.verify(new TwoFactorVerifyRequest("garbage", "123456"), "ip", "ua"));
    }
}
