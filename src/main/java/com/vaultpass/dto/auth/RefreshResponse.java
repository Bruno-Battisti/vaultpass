package com.vaultpass.dto.auth;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
