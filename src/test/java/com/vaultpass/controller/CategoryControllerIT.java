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
import com.vaultpass.dto.category.CategoryRequest;
import com.vaultpass.dto.category.CategoryResponse;
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
class CategoryControllerIT {

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
    void register_seedsSevenDefaultCategories() throws Exception {
        String token = registerAndLogin("category-owner@example.com");

        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.name == 'DESENVOLVIMENTO')]").exists())
                .andExpect(jsonPath("$[?(@.isDefault == true)]").exists());
    }

    @Test
    void crudLifecycle_createUpdateDelete() throws Exception {
        String token = registerAndLogin("category-crud@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Streaming"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Streaming"))
                .andReturn();
        CategoryResponse created =
                objectMapper.readValue(createResult.getResponse().getContentAsString(), CategoryResponse.class);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Streaming"))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/categories/" + created.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Streaming Renomeado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Streaming Renomeado"));

        mockMvc.perform(delete("/api/v1/categories/" + created.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void idor_userCannotModifyAnotherUsersCategory() throws Exception {
        String tokenA = registerAndLogin("cat-owner-a@example.com");
        String tokenB = registerAndLogin("cat-owner-b@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Private"))))
                .andExpect(status().isCreated())
                .andReturn();
        CategoryResponse created =
                objectMapper.readValue(createResult.getResponse().getContentAsString(), CategoryResponse.class);

        mockMvc.perform(put("/api/v1/categories/" + created.id())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Hijacked"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/categories/" + created.id()).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
