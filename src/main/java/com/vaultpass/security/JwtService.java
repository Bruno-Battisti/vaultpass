package com.vaultpass.security;

import com.vaultpass.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates access tokens, plus short-lived 2FA challenge
 * tokens. Refresh tokens are opaque, persisted, single-use values managed
 * by RefreshTokenService — not JWTs — so a refresh token can never be
 * mistaken for a valid access token here.
 */
@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32;
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_2FA_CHALLENGE = "2fa-challenge";
    private static final Duration CHALLENGE_TOKEN_TTL = Duration.ofMinutes(5);

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 256 bits (32 bytes); got " + keyBytes.length + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Duration ttl = Duration.ofMinutes(properties.accessTokenExpirationMinutes());
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Proves the password step of login already succeeded, without granting
     * API access: it carries no role/authorities and is only ever accepted
     * by /auth/2fa/verify (see isChallengeToken, used by JwtAuthenticationFilter
     * to refuse it everywhere else).
     */
    public String generateChallengeToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_PURPOSE, PURPOSE_2FA_CHALLENGE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CHALLENGE_TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    public boolean isChallengeToken(Claims claims) {
        return PURPOSE_2FA_CHALLENGE.equals(claims.get(CLAIM_PURPOSE, String.class));
    }

    public long getAccessTokenExpirationSeconds() {
        return Duration.ofMinutes(properties.accessTokenExpirationMinutes()).toSeconds();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
