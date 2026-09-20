package com.vaultpass.dto.password;

public record PasswordStrengthResponse(PasswordStrength strength, int score) {
}
