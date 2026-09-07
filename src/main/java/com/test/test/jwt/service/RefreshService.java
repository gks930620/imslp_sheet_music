package com.test.test.jwt.service;


import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.common.exception.RefreshTokenException;
import com.test.test.jwt.JwtUtil;
import com.test.test.jwt.entity.RefreshEntity;
import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.repository.RefreshRepository;
import com.test.test.jwt.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshService {
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;


    @Transactional(readOnly = true)
    public RefreshEntity getRefresh(String token) {
        return refreshRepository.findByToken(token);
    }

    @Transactional
    public void saveRefresh(String token) {
        String username = jwtUtil.extractUsername(token);
        refreshRepository.deleteByUserEntity_Username(username);

        RefreshEntity refreshEntity = new RefreshEntity();
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> EntityNotFoundException.of("사용자", username));
        refreshEntity.setUserEntity(user);
        refreshEntity.setToken(token);
        refreshRepository.save(refreshEntity);
    }

    @Transactional
    public void deleteRefresh(String token) {
        refreshRepository.deleteByToken(token);
    }

    /**
     * 사용자의 Refresh 토큰 전부 삭제(로그아웃용).
     * - 토큰 값 완전일치 삭제(deleteByToken)는 클라이언트가 access 토큰을 보내면 매칭 실패한다.
     *   로그아웃 요청은 인증 주체(username)가 확정되므로 username 기준으로 확실히 무효화한다.
     */
    @Transactional
    public void deleteRefreshByUsername(String username) {
        refreshRepository.deleteByUserEntity_Username(username);
    }

    /**
     * Refresh 토큰 회전(rotation) — load → 검증 → 삭제 → 신규 발급을 하나의 트랜잭션으로 처리.
     * - deleteByToken이 반환하는 삭제 행 수를 동시성 가드로 사용(0이면 이미 다른 요청이 회전 → 재사용 차단).
     * - 부분 실패(삭제 후 저장 실패) 시 트랜잭션 롤백으로 기존 토큰이 유지됨.
     * @return access_token / refresh_token 맵
     */
    @Transactional
    public Map<String, String> rotate(String oldToken) {
        if (refreshRepository.findByToken(oldToken) == null) {
            throw new RefreshTokenException("Refresh token is invalid", "TOKEN_DISCARDED");
        }

        if (!jwtUtil.validateToken(oldToken)) {
            refreshRepository.deleteByToken(oldToken);
            throw new RefreshTokenException("Refresh token is expired", "TOKEN_EXPIRED");
        }

        // 즉시 벌크 삭제. 1건이면 회전 주체, 0건이면 동시 요청이 이미 회전함(재사용 차단).
        if (refreshRepository.deleteByToken(oldToken) == 0) {
            throw new RefreshTokenException("Refresh token already rotated", "TOKEN_DISCARDED");
        }

        String username = jwtUtil.extractUsername(oldToken);
        // deleteByToken(clearAutomatically)로 영속성 컨텍스트가 비워지므로 user를 새로 조회
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> EntityNotFoundException.of("사용자", username));

        String newAccessToken = jwtUtil.createAccessToken(username);
        String newRefreshToken = jwtUtil.createRefreshToken(username);

        RefreshEntity entity = new RefreshEntity();
        entity.setUserEntity(user);
        entity.setToken(newRefreshToken);
        refreshRepository.save(entity);

        return Map.of(
                "access_token", newAccessToken,
                "refresh_token", newRefreshToken
        );
    }
}
