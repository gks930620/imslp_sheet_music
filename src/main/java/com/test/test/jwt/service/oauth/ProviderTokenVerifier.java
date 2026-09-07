package com.test.test.jwt.service.oauth;

/**
 * 앱(네이티브) OAuth2 로그인 시 provider access token을 서버가 검증하는 계약.
 *
 * <p>보안 원칙: 앱은 provider(google/kakao)에서 발급받은 access token을 서버로 보내고,
 * 서버는 이 토큰을 provider API로 검증하여 사용자 식별자(sub/id)를 <b>직접</b> 획득한다.
 * 클라이언트가 보낸 id를 그대로 신뢰하면 누구나 임의 사용자로 로그인할 수 있으므로(인증 우회) 금지한다.
 */
public interface ProviderTokenVerifier {

    /**
     * provider access token을 검증하고 사용자 정보를 반환한다.
     * @param provider    소셜 로그인 제공자 (google, kakao 등)
     * @param accessToken 앱이 provider로부터 발급받은 access token
     * @return 검증된 사용자 정보 (서버가 provider로부터 직접 획득)
     * @throws com.test.test.common.exception.BusinessRuleException 토큰이 없거나 검증에 실패한 경우
     */
    VerifiedProviderUser verify(String provider, String accessToken);
}
