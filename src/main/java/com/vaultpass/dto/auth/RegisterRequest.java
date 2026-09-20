package com.vaultpass.dto.auth;

import com.vaultpass.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 150) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @StrongPassword String password
) {
}
