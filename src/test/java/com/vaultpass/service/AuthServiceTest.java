package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.auth.LoginRequest;
import com.vaultpass.dto.auth.RefreshResponse;
import com.vaultpass.dto.auth.RefreshTokenRequest;
import com.vaultpass.dto.auth.RegisterRequest;
import com.vaultpass.dto.auth.UserSummaryResponse;
import com.vaultpass.dto.mapper.UserMapper;
import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.Role;
import com.vaultpass.entity.User;
import com.vaultpass.exception.AccountDisabledException;
import com.vaultpass.exception.AccountLockedException;
import com.vaultpass.exception.DuplicateResourceException;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.repository.UserRepository;
import com.vaultpass.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IP = "203.0.113.5";
    private static final String USER_AGENT = "junit-agent";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CategoryService categoryService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private TwoFactorAuthService twoFactorAuthService;

    @Mock
    private SuspiciousActivityDetector suspiciousActivityDetector;

    @Mock
    private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new AuthService(userRepository, passwordEncoder, jwtService, userMapper, categoryService,
                refreshTokenService, userSessionService, twoFactorAuthService, suspiciousActivityDetector, auditService);
        ReflectionTestUtils.invokeMethod(authService, "init");
    }

    private static AuthResponse asSuccess(LoginResult result) {
        return ((LoginResult.Success) result).authResponse();
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("Bruno", "bruno@example.com", "SenhaForte123!");
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");

        User saved = User.builder().id(UUID.randomUUID()).name(request.name()).email(request.email())
                .passwordHash("hashed").build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserSummaryResponse expected = new UserSummaryResponse(saved.getId(), saved.getName(), saved.getEmail(), Instant.now());
        when(userMapper.toSummary(saved)).thenReturn(expected);

        UserSummaryResponse result = authService.register(request);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).save(any(User.class));
        verify(categoryService).seedDefaultCategories(saved.getId());
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest("Bruno", "bruno@example.com", "SenhaForte123!");
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("bruno@example.com", "SenhaForte123!");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(true).role(Role.USER).failedLoginAttempts(2).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(eq(user.getId()), eq(IP), eq(USER_AGENT)))
                .thenReturn(new RefreshTokenService.IssuedToken(UUID.randomUUID(), "refresh-token"));

        AuthResponse response = asSuccess(authService.login(request, IP, USER_AGENT));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        // Successful login resets the failed-attempts counter.
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(auditService).record(eq(AuditEventType.LOGIN_SUCCESS), eq(user.getId()), eq(IP), eq(USER_AGENT), anyString());
        verify(suspiciousActivityDetector).checkAndRecord(user.getId(), IP, USER_AGENT);
    }

    @Test
    void login_withTwoFactorEnabled_returnsChallengeInsteadOfTokens() {
        LoginRequest request = new LoginRequest("bruno@example.com", "SenhaForte123!");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(true).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(true);
        when(twoFactorAuthService.isEnabledForUser(user.getId())).thenReturn(true);
        when(jwtService.generateChallengeToken(user.getId())).thenReturn("challenge-token");

        LoginResult result = authService.login(request, IP, USER_AGENT);

        assertThat(result).isInstanceOf(LoginResult.TwoFactorRequired.class);
        LoginResult.TwoFactorRequired challenge = (LoginResult.TwoFactorRequired) result;
        assertThat(challenge.challenge().twoFactorRequired()).isTrue();
        assertThat(challenge.challenge().challengeToken()).isEqualTo("challenge-token");
        verify(refreshTokenService, never()).issue(any(), anyString(), anyString());
        verify(auditService, never()).record(eq(AuditEventType.LOGIN_SUCCESS), any(), anyString(), anyString(), anyString());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsAndIncrementsFailedAttempts() {
        LoginRequest request = new LoginRequest("bruno@example.com", "wrong-password");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(true).failedLoginAttempts(0).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, IP, USER_AGENT));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(auditService).record(eq(AuditEventType.LOGIN_FAILED), eq(user.getId()), eq(IP), eq(USER_AGENT), anyString());
    }

    @Test
    void login_fifthConsecutiveFailure_locksAccount() {
        LoginRequest request = new LoginRequest("bruno@example.com", "wrong-password");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(true).failedLoginAttempts(4).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, IP, USER_AGENT));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        verify(auditService).record(eq(AuditEventType.ACCOUNT_LOCKED), eq(user.getId()), eq(IP), eq(USER_AGENT), anyString());
    }

    @Test
    void login_lockedAccount_throwsAccountLockedWithoutCheckingPassword() {
        LoginRequest request = new LoginRequest("bruno@example.com", "SenhaForte123!");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(true).lockedUntil(Instant.now().plusSeconds(600)).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));

        assertThrows(AccountLockedException.class, () -> authService.login(request, IP, USER_AGENT));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_expiredLock_isTreatedAsUnlocked() {
        LoginRequest request = new LoginRequest("bruno@example.com", "SenhaForte123!");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(true).lockedUntil(Instant.now().minusSeconds(1)).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(eq(user.getId()), eq(IP), eq(USER_AGENT)))
                .thenReturn(new RefreshTokenService.IssuedToken(UUID.randomUUID(), "refresh-token"));

        AuthResponse response = asSuccess(authService.login(request, IP, USER_AGENT));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_nonexistentUser_stillRunsPasswordCheckAgainstDummyHash() {
        LoginRequest request = new LoginRequest("ghost@example.com", "whatever");
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq(request.password()), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, IP, USER_AGENT));
        // Proves the timing-attack mitigation: a real Argon2 comparison still runs
        // even when there is no such user, against the precomputed dummy hash.
        verify(passwordEncoder).matches(eq(request.password()), eq("dummy-hash"));
        verify(auditService).record(eq(AuditEventType.LOGIN_FAILED), eq(null), eq(IP), eq(USER_AGENT), anyString());
    }

    @Test
    void login_disabledAccount_throwsAccountDisabled() {
        LoginRequest request = new LoginRequest("bruno@example.com", "SenhaForte123!");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(false).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(true);

        assertThrows(AccountDisabledException.class, () -> authService.login(request, IP, USER_AGENT));
    }

    @Test
    void refresh_success() {
        UUID userId = UUID.randomUUID();
        UUID newTokenId = UUID.randomUUID();
        User user = User.builder().id(userId).enabled(true).build();
        when(refreshTokenService.rotate("valid-refresh", IP, USER_AGENT))
                .thenReturn(new RefreshTokenService.RotationResult(userId, newTokenId, "new-raw-refresh"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        RefreshResponse response = authService.refresh(new RefreshTokenRequest("valid-refresh"), IP, USER_AGENT);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-raw-refresh");
        assertThat(response.expiresIn()).isEqualTo(900L);
        verify(userSessionService).record(userId, newTokenId, IP, USER_AGENT);
    }

    @Test
    void refresh_disabledUser_throwsInvalidCredentials() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).enabled(false).build();
        when(refreshTokenService.rotate("valid-refresh", IP, USER_AGENT))
                .thenReturn(new RefreshTokenService.RotationResult(userId, UUID.randomUUID(), "new-raw-refresh"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.refresh(new RefreshTokenRequest("valid-refresh"), IP, USER_AGENT));
    }

    @Test
    void refresh_propagatesRotationFailure() {
        when(refreshTokenService.rotate("bad-token", IP, USER_AGENT))
                .thenThrow(new InvalidCredentialsException("Invalid or expired refresh token"));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.refresh(new RefreshTokenRequest("bad-token"), IP, USER_AGENT));
    }

    @Test
    void logout_revokesTokenAndRecordsAudit() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.revoke("some-token")).thenReturn(Optional.of(userId));

        authService.logout(new RefreshTokenRequest("some-token"), IP, USER_AGENT);

        ArgumentCaptor<AuditEventType> eventCaptor = ArgumentCaptor.forClass(AuditEventType.class);
        verify(auditService).record(eventCaptor.capture(), eq(userId), eq(IP), eq(USER_AGENT), anyString());
        assertThat(eventCaptor.getValue()).isEqualTo(AuditEventType.LOGOUT);
    }
}
