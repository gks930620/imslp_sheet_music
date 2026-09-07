package com.test.test.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.test.test.jwt.service.oauth.ProviderTokenVerifier;
import com.test.test.jwt.service.oauth.VerifiedProviderUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIntegrationTest extends ApiIntegrationTestSupport {

    // 앱 OAuth 로그인은 provider access token 서버검증을 거친다.
    // 테스트에서는 외부 provider 호출 대신 검증기를 목으로 대체한다.
    @MockBean
    private ProviderTokenVerifier providerTokenVerifier;

    @Test
    void login_api_returns_access_and_refresh_tokens() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "gks930620",
                                  "password": "1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty());
    }

    @Test
    void users_api_creates_user() throws Exception {
        String username = "it_user_" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "1234",
                                  "email": "%s@example.com",
                                  "nickname": "tester"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void users_me_api_returns_current_user() throws Exception {
        Tokens tokens = loginDefaultUser();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("gks930620"));
    }

    @Test
    void tokens_refresh_api_reissues_tokens() throws Exception {
        Tokens tokens = loginDefaultUser();

        mockMvc.perform(post("/api/tokens/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty());
    }

    @Test
    void logout_api_returns_success_message() throws Exception {
        Tokens tokens = loginDefaultUser();

        mockMvc.perform(post("/api/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void oauth2_provider_tokens_api_returns_tokens() throws Exception {
        String socialId = "it-social-" + UUID.randomUUID();
        // provider가 검증해준 것으로 간주되는 사용자 (서버는 클라 id가 아닌 이 값을 신뢰)
        Mockito.when(providerTokenVerifier.verify(eq("google"), anyString()))
                .thenReturn(new VerifiedProviderUser(socialId, "it-social@example.com", "Integration Social"));

        MvcResult result = mockMvc.perform(post("/api/oauth2/providers/google/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "valid-provider-access-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("data").path("access_token").asText()).isNotBlank();
        assertThat(root.path("data").path("refresh_token").asText()).isNotBlank();
    }

    @Test
    void oauth2_provider_tokens_api_rejected_when_no_provider_token() throws Exception {
        // provider access token 없이 요청하면 검증기가 400을 던진다 (인증 우회 차단)
        Mockito.when(providerTokenVerifier.verify(eq("google"), Mockito.any()))
                .thenThrow(new com.test.test.common.exception.BusinessRuleException("provider access token이 필요합니다."));

        mockMvc.perform(post("/api/oauth2/providers/google/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "forged-victim-id"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
