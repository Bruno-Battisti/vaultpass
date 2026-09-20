package com.vaultpass.service;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.auth.LoginRequest;
import com.vaultpass.dto.auth.RefreshResponse;
import com.vaultpass.dto.auth.RefreshTokenRequest;
import com.vaultpass.dto.auth.RegisterRequest;
import com.vaultpass.dto.auth.UserSummaryResponse;
import com.vaultpass.dto.mapper.UserMapper;
import com.vaultpass.entity.User;
import com.vaultpass.exception.AccountDisabledException;
import com.vaultpass.exception.DuplicateResourceException;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.repository.UserRepository;
import com.vaultpass.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login never distinguishes "no such user" from "wrong password": both paths
 * run a real Argon2 comparison (against a dummy hash when the user does not
 * exist) so response timing does not leak account existence.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

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

        return userMapper.toSummary(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException(GENERIC_LOGIN_ERROR);
        }
        if (!user.isEnabled()) {
            throw new AccountDisabledException("Account is disabled");
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public RefreshResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(request.refreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        User user = userRepository.findById(jwtService.extractUserId(claims))
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token"));

        return new RefreshResponse(jwtService.generateAccessToken(user), jwtService.getAccessTokenExpirationSeconds());
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds());
    }
}
