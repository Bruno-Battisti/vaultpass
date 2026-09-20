package com.vaultpass.dto.security;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorEnableRequest(
        @NotBlank String code
) {
}
