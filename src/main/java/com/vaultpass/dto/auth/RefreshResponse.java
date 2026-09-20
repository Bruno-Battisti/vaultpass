package com.vaultpass.dto.auth;

public record RefreshResponse(
        String accessToken,
        long expiresIn
) {
}
