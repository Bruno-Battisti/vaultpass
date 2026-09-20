package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaultpass.dto.password.PasswordGenerateRequest;
import com.vaultpass.dto.password.PasswordStrength;
import com.vaultpass.dto.password.PasswordStrengthResponse;
import com.vaultpass.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordGeneratorServiceTest {

    private PasswordGeneratorService passwordGeneratorService;

    @BeforeEach
    void setUp() {
        passwordGeneratorService = new PasswordGeneratorService();
        ReflectionTestUtils.invokeMethod(passwordGeneratorService, "init");
    }

    @Test
    void generate_respectsRequestedLength() {
        PasswordGenerateRequest request = new PasswordGenerateRequest(24, true, true, true, true);

        String password = passwordGeneratorService.generate(request);

        assertThat(password).hasSize(24);
    }

    @Test
    void generate_onlyUsesRequestedCharacterClasses() {
        PasswordGenerateRequest request = new PasswordGenerateRequest(64, false, true, false, false);

        String password = passwordGeneratorService.generate(request);

        assertThat(password).matches("[a-km-z]+");
    }

    @Test
    void generate_withNoCharacterClassSelected_throwsInvalidRequest() {
        PasswordGenerateRequest request = new PasswordGenerateRequest(16, false, false, false, false);

        assertThrows(InvalidRequestException.class, () -> passwordGeneratorService.generate(request));
    }

    @Test
    void generate_twoCallsProduceDifferentPasswords() {
        PasswordGenerateRequest request = new PasswordGenerateRequest(32, true, true, true, true);

        String first = passwordGeneratorService.generate(request);
        String second = passwordGeneratorService.generate(request);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void calculateStrength_shortSimplePassword_isVeryWeak() {
        PasswordStrengthResponse response = passwordGeneratorService.calculateStrength("abc");

        assertThat(response.strength()).isEqualTo(PasswordStrength.MUITO_FRACA);
    }

    @Test
    void calculateStrength_longRandomPasswordWithAllClasses_isVeryStrong() {
        PasswordStrengthResponse response = passwordGeneratorService.calculateStrength("Xk9#mQ2$vLp8@wRt");

        assertThat(response.strength()).isEqualTo(PasswordStrength.MUITO_FORTE);
    }

    @Test
    void calculateStrength_commonPassword_isCappedLowDespiteLookingComplex() {
        // "password123" is in the embedded common-passwords list; the raw
        // character-class score would otherwise be much higher.
        PasswordStrengthResponse response = passwordGeneratorService.calculateStrength("Password123");

        assertThat(response.score()).isLessThanOrEqualTo(20);
        assertThat(response.strength()).isEqualTo(PasswordStrength.MUITO_FRACA);
    }
}
