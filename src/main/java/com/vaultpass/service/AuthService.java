package com.vaultpass.service;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.auth.LoginRequest;
import com.vaultpass.dto.auth.RefreshResponse;
import com.vaultpass.dto.auth.RefreshTokenRequest;
import com.vaultpass.dto.auth.RegisterRequest;
import com.vaultpass.dto.auth.UserSummaryResponse;
import com.vaultpass.dto.mapper.UserMapper;
import com.vaultpass.entity.AuditEventType;
import com.vaultpass.entity.User;
import com.vaultpass.exception.AccountDisabledException;
import com.vaultpass.exception.AccountLockedException;
import com.vaultpass.exception.DuplicateResourceException;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.repository.UserRepository;
import com.vaultpass.security.JwtService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login never distinguishes "no such user" from "wrong password": both paths
 * run a real Argon2 comparison (against a dummy hash when the user does not
 * exist) so response timing does not leak account existence. A locked
 * account is the one deliberate exception — the account owner benefits from
 * knowing it's locked, so that path short-circuits before the password check.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final CategoryService categoryService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    private String dummyPasswordHash;

    @PostConstruct
    void init() {
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserSummaryResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email already registered");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);
        categoryService.seedDefaultCategories(saved.getId());
        return userMapper.toSummary(saved);
    }

    // noRollbackFor is required: this method deliberately throws
    // InvalidCredentialsException to signal a 401 to the caller, but the
    // failed-attempt counter/lockout bookkeeping written just before that
    // throw must still be committed — otherwise Spring's default rollback
    // on RuntimeException would silently undo every lockout increment.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResponse login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        if (user != null && isLocked(user)) {
            throw new AccountLockedException("Account is locked due to too many failed login attempts");
        }

        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !passwordMatches) {
            if (user != null) {
                registerFailedAttempt(user, ip, userAgent);
            }
            auditService.record(AuditEventType.LOGIN_FAILED, user != null ? user.getId() : null, ip, userAgent,
                    "Failed login attempt for " + request.email());
            throw new InvalidCredentialsException(GENERIC_LOGIN_ERROR);
        }
        if (!user.isEnabled()) {
            throw new AccountDisabledException("Account is disabled");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.record(AuditEventType.LOGIN_SUCCESS, user.getId(), ip, userAgent, "Login successful");

        return buildAuthResponse(user, ip, userAgent);
    }

    @Transactional
    public RefreshResponse refresh(RefreshTokenRequest request, String ip, String userAgent) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken(), ip, userAgent);

        User user = userRepository.findById(rotation.userId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        return new RefreshResponse(
                jwtService.generateAccessToken(user), rotation.rawToken(), jwtService.getAccessTokenExpirationSeconds());
    }

    @Transactional
    public void logout(RefreshTokenRequest request, String ip, String userAgent) {
        refreshTokenService.revoke(request.refreshToken())
                .ifPresent(userId -> auditService.record(AuditEventType.LOGOUT, userId, ip, userAgent, "User logged out"));
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    private void registerFailedAttempt(User user, String ip, String userAgent) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            auditService.record(AuditEventType.ACCOUNT_LOCKED, user.getId(), ip, userAgent,
                    "Account locked after " + attempts + " failed login attempts");
        }
        userRepository.save(user);
    }

    private AuthResponse buildAuthResponse(User user, String ip, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId(), ip, userAgent);
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
    }
}
