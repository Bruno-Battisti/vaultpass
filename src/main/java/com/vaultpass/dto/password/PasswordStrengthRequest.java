package com.vaultpass.dto.password;

import jakarta.validation.constraints.NotBlank;

/**
 * Deliberately a POST body, never a GET query param — a password must never
 * end up in a URL, where it would be captured by access logs, proxies, or
 * browser history.
 */
public record PasswordStrengthRequest(
        @NotBlank String password
) {
}
