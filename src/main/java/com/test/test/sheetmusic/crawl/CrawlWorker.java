package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.edition.LicenseCode;
import com.test.test.sheetmusic.edition.dto.EditionFileUploadDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 수집 워커 (02 §6-10, 03 §3). {@code imslpExecutor} 단일 스레드에서 한 작업씩 처리한다.
 *
 * <p>트랜잭션이 없다 — IMSLP 호출·파일 저장은 트랜잭션 밖에서 하고
 * {@link CrawlPersistService} 로 짧은 DB 트랜잭션만 연다(03 §4).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlWorker {

    /** IMSLP 무응답이 이만큼 연속되면 일시 정지 (02 §6-10). */
    private static final int UNAVAILABLE_THRESHOLD = 3;
    private static final Duration PAUSE_DURATION = Duration.ofMinutes(10);
    private static final long PAUSE_POLL_MILLIS = 5000L;
    private static final String UNAVAILABLE_REASON = "IMSLP가 응답하지 않아요";

    private final ImslpClient imslpClient;
    private final ImslpGate imslpGate;
    private final ImslpWorkPageParser workPageParser;
    private final ImslpWikitextParser wikitextParser;
    private final CrawlPersistService crawlPersistService;
    private final EditionFileFetcher editionFileFetcher;

    @Async("imslpExecutor")
    public void run(Long jobId) {
        try {
            loop(jobId);
        } catch (Exception e) {
            log.error("수집 워커가 예상치 못하게 끝났습니다 - jobId: {}", jobId, e);
            crawlPersistService.markStoppedWithReason(jobId, "수집 중 오류가 났어요");
        }
    }

    private void loop(Long jobId) {
        while (true) {
            CrawlJobState state = crawlPersistService.jobState(jobId);
            if (state == null) {
                return;
            }
            if (state.getStatus() == CrawlJobStatus.PAUSED) {
                if (state.isStopRequested()) {
                    crawlPersistService.markStopped(jobId, false);
                    return;
                }
                if (state.getPausedUntil() != null && Instant.now().isBefore(state.getPausedUntil())) {
                    sleep(PAUSE_POLL_MILLIS);
                    continue;
                }
                // 자동 재시도 시각이 지났다 — 다시 RUNNING 으로 (02 §6-10)
                crawlPersistService.resumeJob(jobId);
                continue;
            }
            if (state.getStatus() != CrawlJobStatus.RUNNING) {
                return;
            }
            if (state.isStopRequested()) {
                crawlPersistService.markStopped(jobId, false);
                return;
            }
            Long itemId = crawlPersistService.nextPendingItemId(jobId);
            if (itemId == null) {
                crawlPersistService.markCompleted(jobId);
                return;
            }
            processItem(jobId, itemId, state.isFetchFiles());
        }
    }

    private void processItem(Long jobId, Long itemId, boolean fetchFiles) {
        CrawlItemEntity item = crawlPersistService.findItem(itemId);
        if (item == null) {
            return;
        }
        String url = item.getImslpUrl();
        CrawlItemMode mode = item.getItemMode();
        crawlPersistService.beginItem(jobId, itemId);

        CrawlUpsertResult upserted = null;
        int fileCount = 0;
        boolean downloadingFiles = false;
        try {
            crawlPersistService.changeStage(jobId, CrawlStage.READING_METADATA, null, null);
            imslpGate.awaitRequestSlot();
            ImslpWorkPage page = imslpClient.fetchWorkPage(url);
            imslpGate.awaitRequestSlot();
            ImslpWikitextPage wikitextPage = imslpClient.fetchWikitext(url);

            ParsedWorkPage parsed = workPageParser.parse(page);
            fillFromWikitext(parsed, wikitextPage);
            boolean pianoSolo = parsed.isPianoSolo(wikitextPage.getCategories());

            upserted = crawlPersistService.upsertWork(parsed, pianoSolo);

            if (!pianoSolo) {
                crawlPersistService.finishItemHidden(jobId, itemId, upserted.getWorkId(),
                        upserted.getEditionCount());
                return;
            }
            // 02 §6-6 — PDF 판본이 하나도 없는 작품 페이지는 받아올 것이 없다.
            if (upserted.getEditionCount() == 0) {
                crawlPersistService.finishItemFailed(jobId, itemId, CrawlFailReason.NO_PDF_EDITION,
                        upserted.getWorkId(), 0, null);
                return;
            }
            if (mode == CrawlItemMode.REFRESH) {
                crawlPersistService.finishItemSuccess(jobId, itemId, upserted.getWorkId(),
                        upserted.getEditionCount(), 0, "판본 정보 갱신 (파일 재요청 없음)");
                return;
            }
            if (fetchFiles) {
                downloadingFiles = true;
                fileCount = fetchFiles(jobId, upserted);
                downloadingFiles = false;
            }
            crawlPersistService.finishItemSuccess(jobId, itemId, upserted.getWorkId(),
                    upserted.getEditionCount(), fileCount,
                    "판본 %d개, 파일 %d개 받음".formatted(upserted.getEditionCount(), fileCount));
        } catch (ImslpPageNotFoundException e) {
            crawlPersistService.finishItemFailed(jobId, itemId, CrawlFailReason.PAGE_NOT_FOUND, null, null, null);
        } catch (FileStageUnavailableException e) {
            // 파일 단계 무응답 — 사유는 IMSLP_UNAVAILABLE 그대로(302·429·5xx 는 "물러서라" 신호라
            // 연속 3항목 PAUSED 백오프를 타야 한다). 단 이미 저장된 판본 수와 그때까지 받은 파일 수는
            // 남긴다 — 관리자가 "곡·판본은 저장됐고 파일만 빈다"를 보고 §5-7 로 재시도할 수 있어야 한다 (02 §6-10).
            crawlPersistService.finishItemFailed(jobId, itemId, CrawlFailReason.IMSLP_UNAVAILABLE,
                    e.getWorkId(), e.getEditionCount(), e.getFileCount());
            pauseOrStopIfUnavailable(jobId);
        } catch (ImslpUnavailableException e) {
            log.warn("메타 읽기 무응답 - itemId: {}, url: {}, 사유: {}", itemId, url, e.getMessage(), e);
            crawlPersistService.finishItemFailed(jobId, itemId, CrawlFailReason.IMSLP_UNAVAILABLE,
                    upserted == null ? null : upserted.getWorkId(), null, null);
            pauseOrStopIfUnavailable(jobId);
        } catch (FileFetchFailedException e) {
            crawlPersistService.finishItemFailed(jobId, itemId, CrawlFailReason.FILE_DOWNLOAD_FAILED,
                    e.getWorkId(), e.getEditionCount(), e.getFileCount());
        } catch (Exception e) {
            // 예상 밖 오류는 뭉개지 않고 단계로 구분한다 (02 §6-10).
            // - 파일 수신 중이면 FILE_DOWNLOAD_FAILED: 곡·판본은 이미 저장됐고 관리자가 §5-7 로 재시도할 수 있다.
            // - 메타 읽기 중이면 INTERNAL_ERROR: 우리 쪽 버그·파싱 사고다. IMSLP_UNAVAILABLE 로 적으면
            //   관리자가 IMSLP 탓으로 읽고, 연속 무응답으로 세어져 멀쩡한 IMSLP 를 두고 10분 멈춘다.
            //   (INTERNAL_ERROR 는 finishItemFailed 에서 연속 무응답 카운터를 초기화한다 → PAUSED 로 가지 않는다.)
            CrawlFailReason reason = downloadingFiles
                    ? CrawlFailReason.FILE_DOWNLOAD_FAILED
                    : CrawlFailReason.INTERNAL_ERROR;
            log.error("수집 항목 처리 중 예상치 못한 오류 - itemId: {}, url: {}, 단계: {}, 사유: {}",
                    itemId, url, downloadingFiles ? "DOWNLOADING_FILE" : "READING_METADATA", reason, e);
            crawlPersistService.finishItemFailed(jobId, itemId, reason,
                    upserted == null ? null : upserted.getWorkId(),
                    upserted == null ? null : upserted.getEditionCount(), null);
        }
    }

    /** 파일 수신 — 하나라도 실패하면 나머지를 마저 받은 뒤 항목을 FAILED 로 만든다 (02 §6-10). */
    private int fetchFiles(Long jobId, CrawlUpsertResult upserted) {
        int total = upserted.getFetchTargets().size();
        if (total == 0) {
            return 0;
        }
        int succeeded = 0;
        boolean failed = false;
        Path directory = null;
        try {
            directory = Files.createTempDirectory("imslp-crawl-");
            int index = 0;
            for (CrawlUpsertResult.FetchTarget target : upserted.getFetchTargets()) {
                index++;
                crawlPersistService.changeStage(jobId, CrawlStage.DOWNLOADING_FILE, index, total);
                try {
                    EditionFileUploadDTO uploaded =
                            editionFileFetcher.downloadAndStore(target.getImslpFileId(), directory);
                    crawlPersistService.changeStage(jobId, CrawlStage.MAKING_PREVIEW, index, total);
                    crawlPersistService.attachFetchedFile(target.getEditionId(), uploaded);
                    succeeded++;
                } catch (ImslpUnavailableException e) {
                    // 무응답도 사유를 남긴다 — 관리자 화면에는 "IMSLP가 응답하지 않아요" 한 줄만 보이므로
                    // 302(봇 게이트)·429·타임아웃 중 무엇이었는지는 로그가 유일한 단서다 (2026-09-07).
                    log.warn("파일 수신 무응답 - imslpFileId: {}, {}/{}번째, 사유: {}",
                            target.getImslpFileId(), index, total, e.getMessage(), e);
                    // 무응답이면 남은 파일을 더 조르지 않고 즉시 빠져나간다 — 다만 진행 상황(판본 수·성공 파일 수)은
                    // 예외에 실어 보낸다. 그냥 던지면 항목이 "판본 0·파일 0" 으로 남아 재시도 판단이 불가능하다.
                    throw new FileStageUnavailableException(e, upserted.getWorkId(),
                            upserted.getEditionCount(), succeeded);
                } catch (Exception e) {
                    log.warn("파일 수신 실패 - imslpFileId: {}, 사유: {}", target.getImslpFileId(), e.getMessage());
                    crawlPersistService.markFetchFailed(target.getEditionId(), CrawlTempFiles.message(e));
                    failed = true;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("임시 디렉터리를 만들지 못했습니다", e);
        } finally {
            CrawlTempFiles.deleteDirectory(directory);
        }
        if (failed) {
            throw new FileFetchFailedException(upserted.getWorkId(), upserted.getEditionCount(), succeeded);
        }
        return succeeded;
    }

    private void pauseOrStopIfUnavailable(Long jobId) {
        CrawlJobState state = crawlPersistService.jobState(jobId);
        if (state == null || state.getConsecutiveUnavailable() < UNAVAILABLE_THRESHOLD) {
            return;
        }
        if (state.getPauseCount() >= 1) {
            crawlPersistService.markStoppedWithReason(jobId, UNAVAILABLE_REASON);
        } else {
            crawlPersistService.markPaused(jobId, Instant.now().plus(PAUSE_DURATION));
        }
    }

    /** HTML 이 마스터 — 위키텍스트는 비어 있는 칸만 채운다 (03 §1). */
    private void fillFromWikitext(ParsedWorkPage parsed, ImslpWikitextPage wikitextPage) {
        ParsedWikitext wikitext = wikitextParser.parse(wikitextPage.getWikitext());
        if (isBlank(parsed.getInstrumentation())) {
            parsed.setInstrumentation(wikitext.workField("Instrumentation"));
        }
        if (isBlank(parsed.getCompositionYear())) {
            parsed.setCompositionYear(wikitext.workField("Year/Date of Composition"));
        }
        for (ParsedEdition edition : parsed.getEditions()) {
            ParsedWikitext.FileBlock block = wikitext.findByFileName(edition.getImslpOriginalFileName());
            if (block == null) {
                continue;
            }
            if (isBlank(edition.getEditor())) {
                edition.setEditor(block.field("Editor"));
            }
            if (isBlank(edition.getArranger())) {
                edition.setArranger(block.field("Arranger"));
            }
            if (isBlank(edition.getScanner())) {
                edition.setScanner(block.field("Scanner"));
            }
            if (isBlank(edition.getImslpCopyrightText())) {
                edition.setImslpCopyrightText(block.field("Copyright"));
                edition.setImslpLicenseCode(
                        LicenseCode.fromText(edition.getImslpCopyrightText()));
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 파일 수신 중 IMSLP 가 무응답이었을 때 — 사유는 {@code IMSLP_UNAVAILABLE} 그대로 두되(백오프 유지)
     * 그때까지의 진행 상황을 함께 나른다 (02 §6-10, 2026-09-07).
     */
    private static class FileStageUnavailableException extends ImslpUnavailableException {

        private final transient Long workId;
        private final int editionCount;
        private final int fileCount;

        FileStageUnavailableException(ImslpUnavailableException cause, Long workId, int editionCount, int fileCount) {
            super(cause.getMessage(), cause);
            this.workId = workId;
            this.editionCount = editionCount;
            this.fileCount = fileCount;
        }

        Long getWorkId() {
            return workId;
        }

        Integer getEditionCount() {
            return editionCount;
        }

        Integer getFileCount() {
            return fileCount;
        }
    }

    /** 파일 수신이 하나라도 실패했을 때 — 판본 정보는 이미 저장돼 있다. */
    private static class FileFetchFailedException extends RuntimeException {

        private final transient Long workId;
        private final int editionCount;
        private final int fileCount;

        FileFetchFailedException(Long workId, int editionCount, int fileCount) {
            super("파일 수신 실패");
            this.workId = workId;
            this.editionCount = editionCount;
            this.fileCount = fileCount;
        }

        Long getWorkId() {
            return workId;
        }

        Integer getEditionCount() {
            return editionCount;
        }

        Integer getFileCount() {
            return fileCount;
        }
    }
}
