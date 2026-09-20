package com.vaultpass.dto.security;

import jakarta.validation.constraints.NotBlank;

/**
 * Requires the master password again: without this, a hijacked access
 * token (but not the password) could be used to strip 2FA off the victim's
 * account.
 */
public record TwoFactorDisableRequest(
        @NotBlank String password
) {
}
