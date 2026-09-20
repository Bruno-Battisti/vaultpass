package com.vaultpass.dto.security;

/**
 * secret is only ever returned here, at setup time — never again after
 * enable() succeeds, so it can't leak through any later read endpoint.
 */
public record TwoFactorSetupResponse(
        String secret,
        String qrCodeImageBase64,
        String otpAuthUrl
) {
}
