package com.vaultpass.dto.password;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PasswordGenerateRequest(
        @Min(8) @Max(128) int length,
        boolean uppercase,
        boolean lowercase,
        boolean numbers,
        boolean symbols
) {
}
