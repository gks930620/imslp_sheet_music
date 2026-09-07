package com.test.test.jwt.service.oauth;

/**
 * provider(구글/카카오 등)로부터 서버가 직접 검증하여 획득한 사용자 정보.
 * 클라이언트가 보낸 값이 아니라 provider API 응답에서 추출한 신뢰 가능한 식별자다.
 *
 * @param id       provider가 부여한 고유 식별자 (google: sub, kakao: id)
 * @param email    이메일 (없을 수 있음, null 허용)
 * @param nickname 표시 이름/닉네임 (없을 수 있음, null 허용)
 */
public record VerifiedProviderUser(String id, String email, String nickname) {
}
