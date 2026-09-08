package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.composer.ComposerEntity;
import com.test.test.sheetmusic.composer.repository.ComposerRepository;
import com.test.test.sheetmusic.crawl.repository.CrawlItemRepository;
import com.test.test.sheetmusic.crawl.repository.CrawlJobRepository;
import com.test.test.sheetmusic.edition.EditionEntity;
import com.test.test.sheetmusic.edition.EditionFileService;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.edition.LicenseCode;
import com.test.test.sheetmusic.edition.dto.EditionFileUploadDTO;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.work.AliasSource;
import com.test.test.sheetmusic.work.HiddenReason;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워커·받아오기가 쓰는 <b>DB 전용</b> 짧은 트랜잭션 모음 (03 §4).
 * 외부 HTTP·파일 저장은 이 클래스 밖(트랜잭션 밖)에서 끝낸 뒤 결과만 넘겨 받는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrawlPersistService {

    private static final int MAX_FILES_PER_WORK = 2;

    private final EditionRepository editionRepository;
    private final WorkRepository workRepository;
    private final ComposerRepository composerRepository;
    private final CrawlJobRepository crawlJobRepository;
    private final CrawlItemRepository crawlItemRepository;
    private final EditionFileService editionFileService;

    // ===== 판본 파일 =====

    @Transactional(readOnly = true)
    public Optional<String> findImslpFileId(Long editionId) {
        return editionRepository.findById(editionId).map(EditionEntity::getImslpFileId);
    }

    @Transactional
    public void markFetching(Long editionId) {
        editionRepository.findById(editionId).ifPresent(EditionEntity::markFetching);
    }

    @Transactional
    public void markFetchFailed(Long editionId, String message) {
        editionRepository.findById(editionId)
                .ifPresent(edition -> edition.markFetchFailed(truncate(message, 300)));
    }

    /**
     * 파일 수신 성공 — files 행 연결 + 판본 갱신 (02 §5-7, 01_ERD §7).
     *
     * <p><b>붙이기 직전에 파일 유무를 다시 본다.</b> §5-7 은 <i>요청 시점</i>에만 "이미 파일이 있는 판본"을 막는데
     * 실제 수신은 수십 초 뒤다(대기 15초 + 전송). 그 사이 관리자가 §5-3 으로 PDF 를 붙였으면 수집 파일이 그것을
     * 밀어내고, 밀려난 {@code files} 행은 {@code ref_id = 판본 id} 라 orphan 배치({@code ref_id = 0} 만 본다)도
     * 못 지워 DB 행과 디스크 바이트가 영구히 남는다. 그래서 <b>관리자 입력이 이기고 받아온 파일을 버린다</b>
     * (수집이 관리자 입력을 덮지 않는다는 이 저장소의 원칙 — 02 §6-10·§6-11 과 같은 방향).
     */
    @Transactional
    public void attachFetchedFile(Long editionId, EditionFileUploadDTO uploaded) {
        EditionEntity edition = editionRepository.findById(editionId).orElse(null);
        if (edition == null) {
            log.warn("받아온 파일을 붙일 판본이 없어 파일을 버립니다 - editionId: {}", editionId);
            discard(uploaded);
            return;
        }
        if (edition.hasFile()) {
            log.info("받아오는 사이 판본에 파일이 생겨 받아온 파일을 버립니다 - editionId: {}", editionId);
            edition.clearFetchRequest();
            discard(uploaded);
            return;
        }
        edition.attachFetchedFile(uploaded.getFileId(), uploaded.getPreviewFileId(),
                uploaded.getPageCount(), Instant.now());
        editionFileService.linkToEdition(editionId, uploaded.getFileId(), uploaded.getPreviewFileId());
    }

    /** 붙이지 못한 수신 파일 정리 — files 행 삭제(바이트는 커밋 후). 아직 연결 전이라 ref_id = 0 이다. */
    private void discard(EditionFileUploadDTO uploaded) {
        editionFileService.deleteFiles(Arrays.asList(uploaded.getFileId(), uploaded.getPreviewFileId()));
    }


    // ===== 곡·판본 upsert (01_ERD §7) =====

    @Transactional
    public CrawlUpsertResult upsertWork(ParsedWorkPage parsed, boolean pianoSolo) {
        ComposerEntity composer = upsertComposer(parsed);
        WorkEntity work = workRepository.findByImslpUrl(parsed.getCanonicalUrl()).orElse(null);
        if (work == null) {
            work = WorkEntity.builder()
                    .composer(composer)
                    .titleOriginal(parsed.getTitleOriginal())
                    .imslpUrl(parsed.getCanonicalUrl())
                    .hidden(!pianoSolo)
                    .hiddenReason(pianoSolo ? null : HiddenReason.NOT_PIANO_SOLO)
                    .build();
            workRepository.save(work);
        } else {
            // 재수집(REFRESH·ATTACH)은 숨김을 다시 계산한다 — 수집이 숨긴 것만 수집이 푼다 (02 §6-11).
            work.reopenIfCrawlerHid(pianoSolo);
        }
        work.fillImslpUrlIfBlank(parsed.getCanonicalUrl());
        work.fillMissingMetadata(parsed.getCompositionYear(), parsed.getMusicalKey(), parsed.getMovements());
        work.addAliases(parsed.getAliases(), AliasSource.IMSLP);
        work.addCatalogNumbers(parsed.getCatalogNumbers());

        List<EditionEntity> editions = new ArrayList<>();
        for (ParsedEdition parsedEdition : parsed.getEditions()) {
            EditionEntity edition = upsertEdition(work, parsedEdition);
            if (edition != null) {
                editions.add(edition);
            }
        }
        workRepository.flush();

        return CrawlUpsertResult.builder()
                .workId(work.getId())
                .editionCount(editions.size())
                .hidden(work.isHidden())
                .fetchTargets(fetchTargets(editions))
                .build();
    }

    private ComposerEntity upsertComposer(ParsedWorkPage parsed) {
        String nameOriginal = parsed.getComposerNameOriginal();
        if (nameOriginal == null || nameOriginal.isBlank()) {
            nameOriginal = "Unknown";
        }
        String normalized = SearchNormalizer.normalize(nameOriginal);
        String finalName = nameOriginal;
        ComposerEntity composer = composerRepository.findByNameOriginalNormalized(normalized)
                .orElseGet(() -> composerRepository.save(ComposerEntity.builder()
                        .nameOriginal(finalName)
                        .imslpUrl(parsed.getComposerImslpUrl())
                        .build()));
        composer.fillImslpUrlIfBlank(parsed.getComposerImslpUrl());
        return composer;
    }

    /**
     * 판본 1개 upsert. {@code imslp_file_id} 는 전역 UNIQUE 라 조회도 전역이다 — 그래서 찾은 판본이
     * <b>다른 곡의 것</b>이면 건드리지 않고 건너뛴다(01_ERD §7, 2026-09-07). 조용히 갱신하거나 곡 사이를
     * 옮겨 다니면 판본 수 집계와 추천이 어긋난다. 건너뛰면 {@code null}.
     */
    private EditionEntity upsertEdition(WorkEntity work, ParsedEdition parsed) {
        EditionEntity edition = editionRepository.findByImslpFileId(parsed.getImslpFileId()).orElse(null);
        if (edition != null && !edition.getWork().getId().equals(work.getId())) {
            log.warn("같은 IMSLP 파일 번호가 다른 곡에 이미 있어 건너뜁니다 - imslpFileId: {}, 기존 곡: {}, 수집 중인 곡: {}",
                    parsed.getImslpFileId(), edition.getWork().getId(), work.getId());
            return null;
        }
        if (edition == null) {
            edition = EditionEntity.builder()
                    .work(work)
                    .kind(parsed.getKind())
                    .scope(parsed.getScope())
                    .movementNumber(parsed.getMovementNumber())
                    .sectionLabel(parsed.getSectionLabel())
                    .imslpDescription(parsed.getImslpDescription())
                    .publisher(parsed.getPublisher())
                    .publishYear(parsed.getPublishYear())
                    .plateNumber(parsed.getPlateNumber())
                    .editor(parsed.getEditor())
                    .arranger(parsed.getArranger())
                    .scanner(parsed.getScanner())
                    .imslpFileId(parsed.getImslpFileId())
                    .imslpOriginalFileName(parsed.getImslpOriginalFileName())
                    .imslpFileUrl(parsed.getImslpFileUrl())
                    .imslpCopyrightText(parsed.getImslpCopyrightText())
                    .imslpLicenseCode(parsed.getImslpLicenseCode())
                    .imslpDownloadCount(parsed.getImslpDownloadCount())
                    .pageCount(parsed.getPageCount())
                    .build();
            editionRepository.save(edition);
        } else {
            edition.refreshFromCrawl(parsed.getKind(), parsed.getScope(), parsed.getMovementNumber(),
                    parsed.getSectionLabel(), parsed.getImslpDescription(), parsed.getPublisher(),
                    parsed.getPublishYear(), parsed.getPlateNumber(), parsed.getEditor(), parsed.getArranger(),
                    parsed.getScanner(), parsed.getImslpOriginalFileName(), parsed.getImslpFileUrl(),
                    parsed.getImslpCopyrightText(), parsed.getImslpLicenseCode(),
                    parsed.getImslpDownloadCount(), parsed.getPageCount());
        }
        return edition;
    }

    /** 01 §9-1 — 전체 악보·전곡·재배포 허용 표기 중 IMSLP 다운로드 수 상위 2개(이미 파일이 있으면 제외). */
    private List<CrawlUpsertResult.FetchTarget> fetchTargets(List<EditionEntity> editions) {
        List<EditionEntity> eligible = new ArrayList<>();
        for (EditionEntity edition : editions) {
            if (edition.getKind() == EditionKind.COMPLETE_SCORE
                    && edition.getScope() == EditionScope.COMPLETE
                    && LicenseCode.isRedistributable(edition.getImslpLicenseCode())
                    && !edition.hasFile()) {
                eligible.add(edition);
            }
        }
        eligible.sort(Comparator
                .comparingInt((EditionEntity e) -> e.getImslpDownloadCount() == null
                        ? Integer.MIN_VALUE : e.getImslpDownloadCount()).reversed()
                .thenComparing(EditionEntity::getId));

        List<CrawlUpsertResult.FetchTarget> targets = new ArrayList<>();
        for (EditionEntity edition : eligible.stream().limit(MAX_FILES_PER_WORK).toList()) {
            targets.add(CrawlUpsertResult.FetchTarget.builder()
                    .editionId(edition.getId())
                    .imslpFileId(edition.getImslpFileId())
                    .build());
        }
        return targets;
    }

    // ===== 작업·항목 상태 (03 §3 — 상태 원본은 DB) =====

    @Transactional(readOnly = true)
    public CrawlJobState jobState(Long jobId) {
        return crawlJobRepository.findById(jobId).map(CrawlJobState::from).orElse(null);
    }

    @Transactional(readOnly = true)
    public Long nextPendingItemId(Long jobId) {
        return crawlItemRepository
                .findFirstByJobIdAndStatusOrderBySeqAsc(jobId, CrawlItemStatus.PENDING)
                .map(CrawlItemEntity::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public CrawlItemEntity findItem(Long itemId) {
        return crawlItemRepository.findById(itemId).orElse(null);
    }

    @Transactional
    public void beginItem(Long jobId, Long itemId) {
        crawlJobRepository.findById(jobId).ifPresent(job -> job.startItem(itemId));
        crawlItemRepository.findById(itemId).ifPresent(CrawlItemEntity::markProcessing);
    }

    @Transactional
    public void changeStage(Long jobId, CrawlStage stage, Integer fileIndex, Integer fileTotal) {
        crawlJobRepository.findById(jobId).ifPresent(job -> job.changeStage(stage, fileIndex, fileTotal));
    }

    @Transactional
    public void finishItemSuccess(Long jobId, Long itemId, Long workId, int editionCount, int fileCount,
                                  String message) {
        crawlItemRepository.findById(itemId)
                .ifPresent(item -> item.markSuccess(workId, editionCount, fileCount, message));
        completeItem(jobId, CrawlItemStatus.SUCCESS, true);
    }

    @Transactional
    public void finishItemHidden(Long jobId, Long itemId, Long workId, int editionCount) {
        crawlItemRepository.findById(itemId).ifPresent(item -> item.markHidden(workId, editionCount));
        completeItem(jobId, CrawlItemStatus.HIDDEN, true);
    }

    @Transactional
    public void finishItemFailed(Long jobId, Long itemId, CrawlFailReason reason, Long workId,
                                 Integer editionCount, Integer fileCount) {
        crawlItemRepository.findById(itemId)
                .ifPresent(item -> item.markFailed(reason, workId, editionCount, fileCount));
        completeItem(jobId, CrawlItemStatus.FAILED, reason != CrawlFailReason.IMSLP_UNAVAILABLE);
    }

    private void completeItem(Long jobId, CrawlItemStatus status, boolean resetUnavailable) {
        crawlJobRepository.findById(jobId).ifPresent(job -> {
            job.countResult(status);
            if (resetUnavailable) {
                job.resetUnavailable();
            } else {
                job.recordUnavailable();
            }
            job.clearProgress();
        });
    }

    @Transactional
    public void markCompleted(Long jobId) {
        crawlJobRepository.findById(jobId).ifPresent(CrawlJobEntity::markCompleted);
    }

    @Transactional
    public void markStopped(Long jobId, boolean byRestart) {
        crawlJobRepository.findById(jobId).ifPresent(job -> job.markStopped(byRestart));
    }

    @Transactional
    public void markStoppedWithReason(Long jobId, String reason) {
        crawlJobRepository.findById(jobId).ifPresent(job -> job.markStoppedWithReason(reason));
    }

    @Transactional
    public void markPaused(Long jobId, Instant pausedUntil) {
        crawlJobRepository.findById(jobId).ifPresent(job -> job.markPaused(pausedUntil));
    }

    /** 자동 재시도 시각이 지난 PAUSED 작업을 다시 RUNNING 으로. */
    @Transactional
    public void resumeJob(Long jobId) {
        crawlJobRepository.findById(jobId).ifPresent(CrawlJobEntity::resume);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
