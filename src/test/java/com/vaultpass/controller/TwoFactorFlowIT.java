package com.vaultpass.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.auth.LoginChallengeResponse;
import com.vaultpass.dto.auth.LoginRequest;
import com.vaultpass.dto.auth.RegisterRequest;
import com.vaultpass.dto.security.TwoFactorDisableRequest;
import com.vaultpass.dto.security.TwoFactorEnableRequest;
import com.vaultpass.dto.security.TwoFactorEnableResponse;
import com.vaultpass.dto.security.TwoFactorSetupResponse;
import com.vaultpass.dto.security.TwoFactorVerifyRequest;
import com.vaultpass.repository.UserRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TwoFactorFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private String currentCodeFor(String secret) throws Exception {
        long counter = Math.floorDiv(new SystemTimeProvider().getTime(), 30);
        return new DefaultCodeGenerator().generate(secret, counter);
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("User", email, "SenhaForte123!"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "SenhaForte123!"))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.accessToken();
    }

    @Test
    void fullTwoFactorLifecycle_setupEnableLoginVerifyRecoveryDisable() throws Exception {
        String accessToken = registerAndLogin("twofactor@example.com");

        // setup
        MvcResult setupResult = mockMvc.perform(post("/api/v1/auth/2fa/setup")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        TwoFactorSetupResponse setup =
                objectMapper.readValue(setupResult.getResponse().getContentAsString(), TwoFactorSetupResponse.class);

        // enable with the first real TOTP code
        MvcResult enableResult = mockMvc.perform(post("/api/v1/auth/2fa/enable")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorEnableRequest(currentCodeFor(setup.secret())))))
                .andExpect(status().isOk())
                .andReturn();
        TwoFactorEnableResponse enable =
                objectMapper.readValue(enableResult.getResponse().getContentAsString(), TwoFactorEnableResponse.class);
        org.assertj.core.api.Assertions.assertThat(enable.recoveryCodes()).hasSize(10);

        // login now returns a challenge instead of tokens
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("twofactor@example.com", "SenhaForte123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();
        LoginChallengeResponse challenge =
                objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginChallengeResponse.class);

        // the challenge token must not work as a Bearer access token anywhere
        mockMvc.perform(post("/api/v1/vault").header("Authorization", "Bearer " + challenge.challengeToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        // wrong TOTP code is rejected
        mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorVerifyRequest(challenge.challengeToken(), "000000"))))
                .andExpect(status().isBadRequest());

        // correct TOTP code completes login
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TwoFactorVerifyRequest(challenge.challengeToken(), currentCodeFor(setup.secret())))))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse verifiedAuth = objectMapper.readValue(verifyResult.getResponse().getContentAsString(), AuthResponse.class);
        org.assertj.core.api.Assertions.assertThat(verifiedAuth.accessToken()).isNotBlank();

        // a recovery code works as an alternative and only once
        MvcResult loginResult2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("twofactor@example.com", "SenhaForte123!"))))
                .andExpect(status().isOk())
                .andReturn();
        LoginChallengeResponse challenge2 =
                objectMapper.readValue(loginResult2.getResponse().getContentAsString(), LoginChallengeResponse.class);
        String recoveryCode = enable.recoveryCodes().get(0);

        mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorVerifyRequest(challenge2.challengeToken(), recoveryCode))))
                .andExpect(status().isOk());

        // reusing the same recovery code must fail
        MvcResult loginResult3 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("twofactor@example.com", "SenhaForte123!"))))
                .andExpect(status().isOk())
                .andReturn();
        LoginChallengeResponse challenge3 =
                objectMapper.readValue(loginResult3.getResponse().getContentAsString(), LoginChallengeResponse.class);
        mockMvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorVerifyRequest(challenge3.challengeToken(), recoveryCode))))
                .andExpect(status().isBadRequest());

        // disable requires the master password
        mockMvc.perform(post("/api/v1/auth/2fa/disable")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorDisableRequest("wrong-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/2fa/disable")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorDisableRequest("SenhaForte123!"))))
                .andExpect(status().isNoContent());

        // login is back to normal (no more challenge)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("twofactor@example.com", "SenhaForte123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
}
