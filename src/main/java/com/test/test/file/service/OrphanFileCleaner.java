package com.test.test.file.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * orphan(버려진 임시) 파일 정리 배치 — code-convention §5-3-1 ③.
 *
 * <p>대상: {@code refId == 0/null} 이면서 {@code createdAt < (now - graceHours)} 인 파일만.
 * ("refId=0 전부 삭제" 금지 — 작성 중 초안 이미지 보호.)
 * 실제 조회/삭제 로직과 트랜잭션 경계는 {@link FileService#deleteOrphanFiles(LocalDateTime)} 가 담당한다:
 * 메타행은 짧은 트랜잭션으로 삭제하고, 저장 바이트(외부 I/O)는 커밋 이후에 삭제한다(§1).
 *
 * <p>시간은 파라미터로 주입해 결정적으로 테스트한다(§6). 스케줄 래퍼는 {@code now = LocalDateTime.now()} 로 호출.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrphanFileCleaner {

    private final FileService fileService;

    /**
     * @param now 기준 시각(테스트에서 시간을 주입해 결정적으로 검증한다)
     * @return 삭제된 파일 수
     */
    public int cleanupOrphans(LocalDateTime now) {
        return fileService.deleteOrphanFiles(now);
    }

    /** 주기 실행 래퍼 — 기본 매일 03:00. ({@code app.file.orphan-cleanup-cron} 로 조정 가능) */
    @Scheduled(cron = "${app.file.orphan-cleanup-cron:0 0 3 * * *}")
    public void scheduledCleanup() {
        int deleted = cleanupOrphans(LocalDateTime.now());
        if (deleted > 0) {
            log.info("orphan 파일 정리 완료 - 삭제 {}건", deleted);
        }
    }
}
