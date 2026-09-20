package com.vaultpass.dto.credential;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * password is optional here: null/blank means "keep the current password"
 * so callers don't have to re-supply (and re-encrypt) it on every edit.
 */
public record CredentialUpdateRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 255) String username,
        @Size(max = 1024) String password,
        @URL @Size(max = 2048) String url,
        @Size(max = 2000) String notes
) {
}
