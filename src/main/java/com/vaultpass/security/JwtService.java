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

@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_REFRESH = "refresh";
    private static final int MIN_KEY_BYTES = 32;

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
        return buildToken(user, Duration.ofMinutes(properties.accessTokenExpirationMinutes()), null);
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, Duration.ofDays(properties.refreshTokenExpirationDays()), TYPE_REFRESH);
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

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    private String buildToken(User user, Duration ttl, String type) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey);
        if (type != null) {
            builder.claim(CLAIM_TYPE, type);
        }
        return builder.compact();
    }
}
