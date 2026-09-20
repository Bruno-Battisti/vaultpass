package com.vaultpass.dto.credential;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CredentialCreateRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 255) String username,
        @NotBlank @Size(max = 1024) String password,
        @URL @Size(max = 2048) String url,
        @Size(max = 2000) String notes
) {
}
