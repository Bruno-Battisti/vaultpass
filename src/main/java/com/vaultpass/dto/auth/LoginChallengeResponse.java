package com.vaultpass.dto.auth;

public record LoginChallengeResponse(
        boolean twoFactorRequired,
        String challengeToken
) {
}
