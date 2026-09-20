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
import com.vaultpass.entity.Role;
import com.vaultpass.entity.User;
import com.vaultpass.exception.AccountDisabledException;
import com.vaultpass.exception.DuplicateResourceException;
import com.vaultpass.exception.InvalidCredentialsException;
import com.vaultpass.repository.UserRepository;
import com.vaultpass.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new AuthService(userRepository, passwordEncoder, jwtService, userMapper, categoryService);
        ReflectionTestUtils.invokeMethod(authService, "init");
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
                .enabled(true).role(Role.USER).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("bruno@example.com", "wrong-password");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(true).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_nonexistentUser_stillRunsPasswordCheckAgainstDummyHash() {
        LoginRequest request = new LoginRequest("ghost@example.com", "whatever");
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq(request.password()), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        // Proves the timing-attack mitigation: a real Argon2 comparison still runs
        // even when there is no such user, against the precomputed dummy hash.
        verify(passwordEncoder).matches(eq(request.password()), eq("dummy-hash"));
    }

    @Test
    void login_disabledAccount_throwsAccountDisabled() {
        LoginRequest request = new LoginRequest("bruno@example.com", "SenhaForte123!");
        User user = User.builder().id(UUID.randomUUID()).email(request.email()).passwordHash("hashed")
                .enabled(false).build();
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "hashed")).thenReturn(true);

        assertThrows(AccountDisabledException.class, () -> authService.login(request));
    }

    @Test
    void refresh_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).enabled(true).build();
        Claims claims = org.mockito.Mockito.mock(Claims.class);

        when(jwtService.parseClaims("valid-refresh")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(jwtService.extractUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        RefreshResponse response = authService.refresh(new RefreshTokenRequest("valid-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void refresh_withAccessTokenInsteadOfRefreshToken_throws() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(jwtService.parseClaims("access-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.refresh(new RefreshTokenRequest("access-token")));
    }

    @Test
    void refresh_malformedToken_throws() {
        when(jwtService.parseClaims("garbage")).thenThrow(new MalformedJwtException("bad token"));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.refresh(new RefreshTokenRequest("garbage")));
    }
}
