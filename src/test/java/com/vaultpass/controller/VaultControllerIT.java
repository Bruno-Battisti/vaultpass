package com.vaultpass.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.auth.LoginRequest;
import com.vaultpass.dto.auth.RegisterRequest;
import com.vaultpass.dto.credential.CredentialCreateRequest;
import com.vaultpass.dto.credential.CredentialResponse;
import com.vaultpass.dto.credential.CredentialUpdateRequest;
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
class VaultControllerIT {

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
    void crudLifecycle_createListGetUpdateDeletePassword() throws Exception {
        String token = registerAndLogin("vault-owner@example.com");

        CredentialCreateRequest createRequest =
                new CredentialCreateRequest("GitHub", "bruno", "s3cr3t-P@ss1", "https://github.com", "main account");
        MvcResult createResult = mockMvc.perform(post("/api/v1/vault")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("GitHub"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        CredentialResponse created =
                objectMapper.readValue(createResult.getResponse().getContentAsString(), CredentialResponse.class);

        mockMvc.perform(get("/api/v1/vault").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("GitHub"));

        mockMvc.perform(get("/api/v1/vault/" + created.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("GitHub"));

        mockMvc.perform(get("/api/v1/vault/" + created.id() + "/password").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value("s3cr3t-P@ss1"));

        CredentialUpdateRequest updateRequest =
                new CredentialUpdateRequest("GitHub Updated", "bruno2", null, "https://github.com", "updated notes");
        mockMvc.perform(put("/api/v1/vault/" + created.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("GitHub Updated"));

        // password stays the same after an update sent with password = null
        mockMvc.perform(get("/api/v1/vault/" + created.id() + "/password").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value("s3cr3t-P@ss1"));

        mockMvc.perform(delete("/api/v1/vault/" + created.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/vault/" + created.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void idor_userCannotAccessAnotherUsersCredential() throws Exception {
        String tokenA = registerAndLogin("owner-a@example.com");
        String tokenB = registerAndLogin("owner-b@example.com");

        CredentialCreateRequest createRequest = new CredentialCreateRequest("Bank", "a", "s3cr3t-P@ss1", null, null);
        MvcResult createResult = mockMvc.perform(post("/api/v1/vault")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        CredentialResponse created =
                objectMapper.readValue(createResult.getResponse().getContentAsString(), CredentialResponse.class);

        mockMvc.perform(get("/api/v1/vault/" + created.id()).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/vault/" + created.id() + "/password").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/vault/" + created.id()).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // credential must still exist for its real owner after B's failed attempts
        mockMvc.perform(get("/api/v1/vault/" + created.id()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void create_withoutAuthentication_returnsUnauthorized() throws Exception {
        CredentialCreateRequest createRequest = new CredentialCreateRequest("GitHub", "bruno", "s3cr3t-P@ss1", null, null);
        mockMvc.perform(post("/api/v1/vault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isUnauthorized());
    }
}
