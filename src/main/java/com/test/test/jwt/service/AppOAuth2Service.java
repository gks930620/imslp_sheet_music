package com.test.test.jwt.service;

import com.test.test.jwt.JwtUtil;
import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.service.oauth.ProviderTokenVerifier;
import com.test.test.jwt.service.oauth.VerifiedProviderUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 앱(네이티브) OAuth2 로그인 처리 서비스
 * - 앱에서 전달한 provider 사용자 정보로 회원을 조회/생성하고 JWT를 발급한다.
 * - Controller는 이 서비스만 호출한다. (Repository/Entity 직접 접근 금지 원칙 준수)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppOAuth2Service {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RefreshService refreshService;
    private final ProviderTokenVerifier providerTokenVerifier;
    private final OAuthUserWriter oAuthUserWriter;

    /**
     * 앱 OAuth2 로그인: provider access token 검증 후 사용자 upsert + JWT 발급.
     *
     * <p>보안: 클라이언트가 보낸 id를 신뢰하지 않는다. 앱은 provider access token
     * (accessToken/access_token)을 전달하고, 서버가 이를 provider에 검증하여 사용자 식별자를
     * 직접 획득한다. (검증 없이 id를 신뢰하면 누구나 임의 계정으로 로그인 가능 = 인증 우회)
     *
     * @param provider 소셜 로그인 제공자 (google, kakao 등)
     * @param request  앱에서 전달한 값 (accessToken 필수)
     * @return access_token, refresh_token 맵
     */
    public Map<String, String> login(String provider, Map<String, String> request) {
        String providerAccessToken = request.get("accessToken") != null
                ? request.get("accessToken")
                : request.get("access_token");

        // ① 외부 HTTP: provider access token을 서버가 직접 검증 → 신뢰 가능한 식별자 획득. (§1: 트랜잭션 밖)
        VerifiedProviderUser verified = providerTokenVerifier.verify(provider, providerAccessToken);

        String username = provider + verified.id();
        String defaultNickname = "google".equalsIgnoreCase(provider) ? "Google User" : "Kakao User";

        // ② 검증 결과로 후보 엔티티 조립 후 DB upsert만 짧은 트랜잭션으로 처리(§1).
        UserEntity candidate = UserEntity.builder()
                .username(username)
                .email(verified.email() != null ? verified.email() : "")
                .nickname(verified.nickname() != null ? verified.nickname() : defaultNickname)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .provider(provider.toLowerCase())
                .roles(new ArrayList<>(List.of("USER")))
                .isActive(true)
                .build();
        oAuthUserWriter.upsert(candidate);

        String accessToken = jwtUtil.createAccessToken(username);
        String refreshToken = jwtUtil.createRefreshToken(username);
        refreshService.saveRefresh(refreshToken);

        return Map.of(
                "access_token", accessToken,
                "refresh_token", refreshToken
        );
    }
}
