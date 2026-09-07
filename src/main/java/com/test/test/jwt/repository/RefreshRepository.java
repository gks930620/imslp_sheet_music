package com.test.test.jwt.repository;

import com.test.test.jwt.entity.RefreshEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshRepository  extends JpaRepository<RefreshEntity,Long> {
    // 즉시 벌크 삭제. 로그인 시 기존 토큰 삭제가 신규 INSERT보다 확실히 먼저 반영되어,
    // 같은 초 재로그인(동일 토큰 재발급) 시 token 유니크 충돌(500)을 방지.
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshEntity r where r.userEntity.username = :username")
    void deleteByUserEntity_Username(@Param("username") String username);

    boolean existsByUserEntity_Username(String username);

    // 즉시 실행 벌크 삭제 + 삭제 행 수 반환.
    // - 즉시 DELETE라 뒤이은 INSERT(재발급 토큰)보다 확실히 먼저 반영 → 동일 토큰 재발급 시 유니크 충돌 방지
    // - 반환값 0이면 동시 요청이 이미 회전한 것으로 간주(재사용 차단)
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshEntity r where r.token = :token")
    int deleteByToken(@Param("token") String token);

    public RefreshEntity findByToken(String token);
}
