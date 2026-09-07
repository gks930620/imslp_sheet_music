package com.test.test.jwt.service.oauth;

import com.test.test.common.exception.BusinessRuleException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * provider access token을 실제 provider API로 검증하는 기본 구현.
 * - Google: userinfo 엔드포인트로 access token 검증 (sub/email/name 획득)
 * - Kakao:  /v2/user/me 로 access token 검증 (id/email/nickname 획득)
 *
 * 검증 실패(만료/위조/네트워크 오류 등)는 모두 400 BusinessRuleException으로 변환한다.
 */
@Slf4j
@Component
public class HttpProviderTokenVerifier implements ProviderTokenVerifier {

    private static final String GOOGLE_USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String KAKAO_USERINFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient = RestClient.create();

    @Override
    public VerifiedProviderUser verify(String provider, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessRuleException("provider access token이 필요합니다.");
        }
        if (provider == null) {
            throw new BusinessRuleException("provider가 필요합니다.");
        }

        return switch (provider.toLowerCase()) {
            case "google" -> verifyGoogle(accessToken);
            case "kakao" -> verifyKakao(accessToken);
            default -> throw new BusinessRuleException("지원하지 않는 provider입니다: " + provider);
        };
    }

    private VerifiedProviderUser verifyGoogle(String accessToken) {
        Map<String, Object> body = fetchUserInfo(GOOGLE_USERINFO_URI, accessToken, "google");
        String sub = asString(body.get("sub"));
        if (sub == null) {
            throw new BusinessRuleException("google 토큰 검증 실패: 사용자 식별자를 확인할 수 없습니다.");
        }
        return new VerifiedProviderUser(sub, asString(body.get("email")), asString(body.get("name")));
    }

    @SuppressWarnings("unchecked")
    private VerifiedProviderUser verifyKakao(String accessToken) {
        Map<String, Object> body = fetchUserInfo(KAKAO_USERINFO_URI, accessToken, "kakao");
        String id = asString(body.get("id"));
        if (id == null) {
            throw new BusinessRuleException("kakao 토큰 검증 실패: 사용자 식별자를 확인할 수 없습니다.");
        }
        String email = null;
        String nickname = null;
        if (body.get("kakao_account") instanceof Map<?, ?> account) {
            email = asString(account.get("email"));
            if (account.get("profile") instanceof Map<?, ?> profile) {
                nickname = asString(profile.get("nickname"));
            }
        }
        if (nickname == null && body.get("properties") instanceof Map<?, ?> props) {
            nickname = asString(props.get("nickname"));
        }
        return new VerifiedProviderUser(id, email, nickname);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String uri, String accessToken, String provider) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                throw new BusinessRuleException(provider + " 토큰 검증 실패: 응답이 비어 있습니다.");
            }
            return body;
        } catch (RestClientException e) {
            log.warn("{} provider 토큰 검증 실패: {}", provider, e.getMessage());
            throw new BusinessRuleException(provider + " 토큰 검증에 실패했습니다.");
        }
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
