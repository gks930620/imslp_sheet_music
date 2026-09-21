package com.test.test.sheetmusic.member;

import com.test.test.file.entity.FileEntity;
import com.test.test.file.repository.FileRepository;
import com.test.test.jwt.repository.UserRepository;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.member.repository.UserWorkDownloadRepository;
import com.test.test.sheetmusic.work.WorkEntity;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다운로드 1건을 "받은 악보" 선반에 올린다 (01_ERD §7 · §3-12).
 *
 * <p><b>다운로드 카운터와 같은 트랜잭션에서 돈다</b> — 그래서 새 트랜잭션을 열지 않고 {@link Propagation#MANDATORY}
 * 로 요구한다. 집계({@code download_log})와 선반이 따로 커밋되면 "받았는데 선반에 없다" 가 생긴다.
 *
 * <p>비로그인 다운로드는 여기 오지 않는다(호출자가 주체 없으면 부르지 않는다) — 그 기록은 누구의 것도 아니다
 * (기획 05 §3-1). {@code HEAD} 도 마찬가지로 이 경로를 지나지 않는다(02 §3-4).
 *
 * <p><b>왜 "처음 받는 곡" 한 갈래만 네이티브 한 문장인가</b> (컨벤션 §1 · 03 §26): 같은 계정이 한 번도 안 받은
 * 곡을 같은 순간에 두 번 받으면 "있나 보고(없으면) 넣는다" 의 두 요청이 같은 창에 들어와 뒤에 온 쪽이
 * {@code uk_user_work_download} 를 위반한다. 제약 위반은 그 트랜잭션을 <b>rollback-only</b> 로 만들어
 * {@code try/catch} 로 피할 수 없고(커밋이 다시 {@code UnexpectedRollbackException} 으로 터진다),
 * 여기는 다운로드 본체 트랜잭션 <b>안</b>({@link Propagation#MANDATORY})이라 즐겨찾기({@code FavoriteWriter})처럼
 * 쓰기를 떼어낼 수도 없다 — {@code REQUIRES_NEW} 로 떼면 ① 커밋이 둘로 갈려 "집계와 선반은 한 트랜잭션"(§24)이
 * 깨지고 ② 안쪽이 다른 커넥션이라 바깥이 잠근 FK 부모 행을 자기가 기다리고 ③ REPEATABLE READ 에서는 위반 뒤
 * 다시 읽어도 겹친 요청이 커밋한 행이 안 보인다(03 §26-1). 그래서 위반을 "처리" 하지 않고 <b>일어나지 않게</b>
 * 한다 — 읽고-나서-넣는 창 자체를 {@code INSERT … ON DUPLICATE KEY UPDATE} 한 문장으로 없앤다.
 *
 * <p>위험한 갈래는 그 하나뿐이라 <b>조회와 "있으면 수정" 경로는 그대로 둔다</b> — 줄이 이미 있으면 그 행의
 * 잠금이 동시 요청을 줄 세우므로 제약을 위반할 길이 없고, 흔한 길(다시 받기)은 JPA 의 dirty checking 이 읽기 쉽다.
 * 검증: {@code MyLibraryDownloadRaceIntegrationTest}.
 */
@Service
@RequiredArgsConstructor
public class MyLibraryRecorder {

    private final UserWorkDownloadRepository userWorkDownloadRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    /** 곡 단위 upsert — 같은 곡을 다시 받으면 행이 늘지 않고 마지막 판본·스냅샷·시각만 덮인다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDownload(Long userId, WorkEntity work, EditionEntity edition, Instant downloadedAt) {
        Long fileSize = fileSize(edition);
        userWorkDownloadRepository.findByUserIdAndWorkId(userId, work.getId())
                .ifPresentOrElse(
                        row -> row.record(edition, fileSize, downloadedAt),
                        () -> insert(userId, work, edition, fileSize, downloadedAt));
    }

    /**
     * 처음 받는 곡 — 겹친 요청이 끼어들 창이 없도록 <b>한 문장</b>으로 넣는다(클래스 javadoc · 03 §26-2).
     *
     * <p>스냅샷 5개를 판본에서 뽑는 규칙은 <b>엔티티가 그대로 갖고</b>, 여기서는 엔티티가 계산한 값을 파라미터로
     * 옮겨 담기만 한다 — SQL 이 판본에서 다시 계산하면 같은 규칙이 두 곳에 생긴다. 그래서 넣으려는 줄을
     * <b>엔티티로 그대로 만든 뒤</b> 그 값을 읽는다({@code save} 대신 우리가 문장을 쓸 뿐, 넣는 값은 전과 같다).
     * {@code created_at} 도 예외가 아니다 — 그 값을 정하는 곳은 엔티티 생성자 하나이고(03 §26-4) 여기서는
     * 나머지 9개와 똑같이 {@code row.getCreatedAt()} 을 읽는다. 그래서 <b>파라미터 10개가 전부</b>
     * 엔티티가 계산한 값이다.
     */
    private void insert(Long userId, WorkEntity work, EditionEntity edition, Long fileSize, Instant downloadedAt) {
        UserWorkDownloadEntity row = new UserWorkDownloadEntity(
                userRepository.getReferenceById(userId), work, edition, fileSize, downloadedAt);
        userWorkDownloadRepository.upsert(
                userId,
                work.getId(),
                row.getLastDownloadedAt(),
                row.getLastEdition() == null ? null : row.getLastEdition().getId(),
                nameOf(row.getEditionKind()),
                nameOf(row.getEditionScope()),
                row.getEditionMovementNumber(),
                row.getEditionPageCount(),
                row.getEditionFileSize(),
                row.getCreatedAt());
    }

    /** 스냅샷 enum 2개는 {@code varchar} 컬럼이다(01_ERD §9-1) — 네이티브 문장에는 이름 문자열로 넘긴다. */
    private String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /** 스냅샷의 크기는 화면의 "1.1MB" 와 같은 값이어야 하므로 {@code files} 행에서 읽는다(02 §2-4). */
    private Long fileSize(EditionEntity edition) {
        if (edition.getPdfFileId() == null) {
            return null;
        }
        return fileRepository.findById(edition.getPdfFileId())
                .map(FileEntity::getFileSize)
                .orElse(null);
    }
}
