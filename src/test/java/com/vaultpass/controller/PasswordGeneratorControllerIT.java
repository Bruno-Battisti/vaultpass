package com.vaultpass.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.auth.LoginRequest;
import com.vaultpass.dto.auth.RegisterRequest;
import com.vaultpass.dto.password.PasswordGenerateRequest;
import com.vaultpass.dto.password.PasswordStrengthRequest;
import com.vaultpass.repository.UserRepository;
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
class PasswordGeneratorControllerIT {

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
    void generate_returnsPasswordOfRequestedLength() throws Exception {
        String token = registerAndLogin("pwgen@example.com");

        mockMvc.perform(post("/api/v1/password/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordGenerateRequest(20, true, true, true, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password", org.hamcrest.Matchers.hasLength(20)));
    }

    @Test
    void generate_withNoCharacterClassSelected_returnsBadRequest() throws Exception {
        String token = registerAndLogin("pwgen-invalid@example.com");

        mockMvc.perform(post("/api/v1/password/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordGenerateRequest(20, false, false, false, false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void strength_viaPostBody_returnsScore() throws Exception {
        String token = registerAndLogin("pwstrength@example.com");

        mockMvc.perform(post("/api/v1/password/strength")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordStrengthRequest("abc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strength").value("MUITO_FRACA"));
    }

    @Test
    void endpoints_withoutAuthentication_returnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/password/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordGenerateRequest(20, true, true, true, true))))
                .andExpect(status().isUnauthorized());
    }
}
