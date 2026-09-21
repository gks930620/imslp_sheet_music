package com.test.test.sheetmusic.member;

import com.test.test.jwt.repository.UserRepository;
import com.test.test.sheetmusic.member.repository.WorkFavoriteRepository;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 즐겨찾기 한 행 넣기 (02 §10-1) — <b>DB 작업만</b> 하는 짧은 쓰기 트랜잭션.
 *
 * <p>{@link FavoriteService} 에서 떼어 둔 이유는 <b>트랜잭션 경계</b> 하나다. "있나 보고 없으면 넣기" 는
 * 같은 순간 다른 요청이 끼면 {@code uk_work_favorite} 을 위반하는데, 제약 위반은 그 트랜잭션을
 * <b>rollback-only</b> 로 만든다 — 같은 트랜잭션 안에서 예외를 삼켜 봐야 커밋이 다시
 * {@code UnexpectedRollbackException} 으로 터져 500 이 된다(확인: {@code FavoriteTurnOnRaceIntegrationTest}).
 * 그래서 넣기는 여기서 혼자 열고 닫고, 실패는 트랜잭션이 <b>끝난 뒤</b> 호출자가 받는다.
 *
 * <p>메서드가 {@code public} 인 것은 의도다 — 프록시 기반 트랜잭션은 public 메서드에만 걸린다.
 * (클래스는 이 패키지 밖에서 쓸 일이 없어 package-private 이다.)
 */
@Component
@RequiredArgsConstructor
class FavoriteWriter {

    private final WorkFavoriteRepository workFavoriteRepository;
    private final UserRepository userRepository;
    private final WorkRepository workRepository;

    /**
     * 이미 있으면 아무것도 하지 않는다 — 행을 다시 만들지 않고 {@code created_at} 도 건드리지 않는다
     * (§10-2 "최근에 넣은 순" 이 흔들리지 않게).
     *
     * @throws DataIntegrityViolationException 있나 본 뒤 넣기 전에 다른 요청이 같은 행을 넣었을 때
     */
    @Transactional
    public void insertIfAbsent(Long userId, Long workId) {
        if (workFavoriteRepository.existsByUserIdAndWorkId(userId, workId)) {
            return;
        }
        workFavoriteRepository.save(new WorkFavoriteEntity(
                userRepository.getReferenceById(userId), workRepository.getReferenceById(workId)));
    }
}
