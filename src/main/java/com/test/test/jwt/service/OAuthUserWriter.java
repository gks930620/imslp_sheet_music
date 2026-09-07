package com.test.test.jwt.service;

import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 사용자 upsert 전용 협력자.
 *
 * <p>§1 준수: OAuth 외부 HTTP 호출(provider 토큰 검증 / userinfo 조회)은 <b>이 빈을 호출하기 전에</b>
 * 트랜잭션 밖에서 끝나 있어야 한다. 이 빈은 그 결과로 <b>DB upsert만 담는 짧은 트랜잭션</b>을 연다.
 * (별도 빈으로 두어 self-invocation 없이 프록시 트랜잭션이 확실히 적용되도록 한다.)
 */
@Service
@RequiredArgsConstructor
public class OAuthUserWriter {

    private final UserRepository userRepository;

    /**
     * username 기준 upsert. 기존 사용자면 프로필(이메일·닉네임)만 갱신, 없으면 신규 저장.
     * @param candidate 외부 검증 결과로 조립한 사용자 정보(username 필수)
     * @return 영속화된 사용자 엔티티
     */
    @Transactional
    public UserEntity upsert(UserEntity candidate) {
        return userRepository.findByUsername(candidate.getUsername())
                .map(existing -> {
                    existing.updateProfile(candidate.getEmail(), candidate.getNickname());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(candidate));
    }
}
