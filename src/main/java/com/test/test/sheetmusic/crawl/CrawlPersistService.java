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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워커·받아오기가 쓰는 <b>DB 전용</b> 짧은 트랜잭션 모음 (03 §4).
 * 외부 HTTP·파일 저장은 이 클래스 밖(트랜잭션 밖)에서 끝낸 뒤 결과만 넘겨 받는다.
 */
@Service
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

    /** 파일 수신 성공 — files 행 연결 + 판본 갱신. */
    @Transactional
    public void attachFetchedFile(Long editionId, EditionFileUploadDTO uploaded) {
        editionRepository.findById(editionId).ifPresent(edition -> {
            edition.attachFetchedFile(uploaded.getFileId(), uploaded.getPreviewFileId(),
                    uploaded.getPageCount(), Instant.now());
            editionFileService.linkToEdition(editionId, uploaded.getFileId(), uploaded.getPreviewFileId());
        });
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
        }
        work.fillImslpUrlIfBlank(parsed.getCanonicalUrl());
        work.fillMissingMetadata(parsed.getCompositionYear(), parsed.getMusicalKey(), parsed.getMovements());
        work.addAliases(parsed.getAliases(), AliasSource.IMSLP);
        work.addCatalogNumbers(parsed.getCatalogNumbers());

        List<EditionEntity> editions = new ArrayList<>();
        for (ParsedEdition parsedEdition : parsed.getEditions()) {
            editions.add(upsertEdition(work, parsedEdition));
        }
        workRepository.flush();

        return CrawlUpsertResult.builder()
                .workId(work.getId())
                .editionCount(parsed.getEditions().size())
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

    private EditionEntity upsertEdition(WorkEntity work, ParsedEdition parsed) {
        EditionEntity edition = editionRepository.findByImslpFileId(parsed.getImslpFileId()).orElse(null);
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
