package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "file.upload-dir=./build/test-uploads",
        "app.cookie.secure=false"
})
@Transactional
public abstract class ApiIntegrationTestSupport {

    /** 시드 계정 (data-users.sql / data-user-roles.sql). gks930620 은 USER + ADMIN, user4 는 USER 만. */
    protected static final String ADMIN_USERNAME = "gks930620";
    protected static final String USER_USERNAME = "user4";
    private static final String SEED_PASSWORD = "1234";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected record Tokens(String accessToken, String refreshToken) {
    }

    /** 기존 테스트 호환용 — gks930620 (ADMIN 역할도 가진 시드 계정). */
    protected Tokens loginDefaultUser() throws Exception {
        return login(ADMIN_USERNAME, SEED_PASSWORD);
    }

    /** 관리자(ADMIN) 로그인 — gks930620/1234. 관리 API(/api/admin/**) 테스트용. */
    protected Tokens loginAdmin() throws Exception {
        return login(ADMIN_USERNAME, SEED_PASSWORD);
    }

    /** 일반 사용자(USER) 로그인 — user4/1234. 관리 API 403 검증용. */
    protected Tokens loginUser() throws Exception {
        return login(USER_USERNAME, SEED_PASSWORD);
    }

    protected Tokens login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Tokens(root.path("access_token").asText(), root.path("refresh_token").asText());
    }

    protected String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @AfterEach
    void cleanupUploadedFiles() throws Exception {
        Path testUploadRoot = Path.of("build", "test-uploads").toAbsolutePath().normalize();
        if (!Files.exists(testUploadRoot)) {
            return;
        }

        Files.walk(testUploadRoot)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    if (!path.equals(testUploadRoot)) {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    }
                });
    }
}
