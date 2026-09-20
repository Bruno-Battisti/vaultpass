package com.vaultpass.dto.security;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
        @NotBlank String challengeToken,
        @NotBlank String code
) {
}
