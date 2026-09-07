package com.test.test.sheetmusic.edition;

import com.test.test.common.dto.PageResponse;
import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.common.exception.FieldValidationException;
import com.test.test.file.entity.FileEntity;
import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import com.test.test.sheetmusic.common.SearchNormalizer;
import com.test.test.sheetmusic.crawl.EditionFileFetcher;
import com.test.test.sheetmusic.edition.dto.AdminEditionDTO;
import com.test.test.sheetmusic.edition.dto.CopyrightDTOs;
import com.test.test.sheetmusic.edition.dto.EditionSaveDTO;
import com.test.test.sheetmusic.edition.repository.DownloadLogRepository;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.work.WorkEntity;
import com.test.test.sheetmusic.work.repository.WorkRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판본 관리 (02 §5-2 ~ §5-10).
 *
 * <p>클래스에 {@code @Transactional(readOnly = true)} 를 걸지 않는다(컨벤션 §1 기본형에서 벗어남) —
 * 저장(§5-2·§5-3)의 쪽수 폴백이 저장소 읽기 + PDFBox 파싱이라 <b>트랜잭션에 들어가기 전에</b> 끝내야 하는데,
 * 클래스 기본 트랜잭션이 걸려 있으면 그 외부 I/O 가 읽기 트랜잭션 안에서 돌게 된다.
 * 대신 조회 메서드에 {@code readOnly} 를, 쓰기 메서드에 {@code @Transactional} 을 개별로 붙이고,
 * 저장은 {@link TransactionTemplate} 으로 트랜잭션 경계를 명시한다.
 */
@Service
@RequiredArgsConstructor
public class AdminEditionService {

    private final EditionRepository editionRepository;
    private final WorkRepository workRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final EditionFileService editionFileService;
    private final EditionDtoAssembler editionDtoAssembler;
    private final EditionFileFetcher editionFileFetcher;
    private final TransactionTemplate transactionTemplate;

    // ===== §5-2 / §5-3 저장 =====

    /** 얇은 진입점 — 쪽수 폴백(외부 I/O)을 먼저 끝내고, 그 결과로 짧은 쓰기 트랜잭션만 연다(컨벤션 §1). */
    public AdminEditionDTO create(Long workId, EditionSaveDTO request, String username) {
        Integer resolvedPageCount = request.getPageCount() == null
                ? editionFileService.pageCountOfUploadedFile(request.getFileId())
                : request.getPageCount();
        return transactionTemplate.execute(status -> createInTransaction(workId, request, username,
                resolvedPageCount));
    }

    private AdminEditionDTO createInTransaction(Long workId, EditionSaveDTO request, String username,
                                                Integer resolvedPageCount) {
        WorkEntity work = workRepository.findById(workId)
                .orElseThrow(() -> EntityNotFoundException.of("곡", workId));
        validate(request);

        Integer pageCount = request.getPageCount();
        if (request.getFileId() != null) {
            editionFileService.requireUnlinkedEditionFile(request.getFileId());
            if (pageCount == null) {
                pageCount = resolvedPageCount;
            }
        }

        EditionEntity edition = EditionEntity.builder()
                .work(work)
                .kind(request.getKind())
                .scope(request.getScope())
                .movementNumber(request.getScope() == EditionScope.MOVEMENT ? request.getMovementNumber() : null)
                .publisher(request.getPublisher())
                .publishYear(request.getPublishYear())
                .plateNumber(request.getPlateNumber())
                .editor(request.getEditor())
                .arranger(request.getArranger())
                .scanner(request.getScanner())
                .imslpFileUrl(request.getImslpFileUrl())
                .imslpCopyrightText(request.getImslpCopyrightText())
                .pageCount(pageCount)
                .koreaCopyright(request.getKoreaCopyright())
                .copyrightNote(request.getCopyrightNote())
                .ccLicenseName(request.getCcLicenseName())
                .ccAttribution(request.getCcAttribution())
                .build();
        if (request.getKoreaCopyright() != KoreaCopyright.UNKNOWN) {
            edition.judgeCopyright(request.getKoreaCopyright(), request.getCopyrightNote(), username, Instant.now());
        }
        edition.changeFiles(request.getFileId(), request.getPreviewFileId());
        editionRepository.save(edition);
        editionFileService.linkToEdition(edition.getId(), request.getFileId(), request.getPreviewFileId());

        return toDetailDto(edition, work);
    }

    /** 얇은 진입점 — {@link #create} 와 같은 이유로 쪽수 폴백을 트랜잭션 밖에서 먼저 계산한다. */
    public AdminEditionDTO update(Long editionId, EditionSaveDTO request, String username) {
        Integer resolvedPageCount = resolveNewFilePageCount(editionId, request);
        return transactionTemplate.execute(status -> updateInTransaction(editionId, request, username,
                resolvedPageCount));
    }

    /**
     * 파일이 실제로 바뀌었고 요청에 쪽수가 없을 때만 새 파일의 쪽수를 읽는다(외부 I/O — 트랜잭션 밖).
     * 판본이 없거나 파일이 그대로면 null — 그 판정·오류는 트랜잭션 안에서 다시 내린다.
     */
    private Integer resolveNewFilePageCount(Long editionId, EditionSaveDTO request) {
        if (request.getPageCount() != null || request.getFileId() == null) {
            return request.getPageCount();
        }
        Long currentPdfFileId = editionRepository.findById(editionId)
                .map(EditionEntity::getPdfFileId)
                .orElse(null);
        if (Objects.equals(currentPdfFileId, request.getFileId())) {
            return null;
        }
        return editionFileService.pageCountOfUploadedFile(request.getFileId());
    }

    private AdminEditionDTO updateInTransaction(Long editionId, EditionSaveDTO request, String username,
                                                Integer resolvedPageCount) {
        EditionEntity edition = findOrThrow(editionId);
        validate(request);
        WorkEntity work = edition.getWork();

        Long oldPdfFileId = edition.getPdfFileId();
        Long oldPreviewFileId = edition.getPreviewFileId();
        boolean fileChanged = !Objects.equals(oldPdfFileId, request.getFileId());

        Integer pageCount = request.getPageCount();
        if (fileChanged) {
            if (request.getFileId() != null) {
                editionFileService.requireUnlinkedEditionFile(request.getFileId());
                if (pageCount == null) {
                    pageCount = resolvedPageCount;
                }
            }
        } else if (pageCount == null) {
            pageCount = edition.getPageCount();
        }

        edition.updateFromAdmin(request.getKind(), request.getScope(), request.getMovementNumber(), pageCount,
                request.getPublisher(), request.getPublishYear(), request.getPlateNumber(), request.getEditor(),
                request.getArranger(), request.getScanner(), request.getImslpFileUrl(),
                request.getImslpCopyrightText(), request.getCcLicenseName(), request.getCcAttribution());
        edition.applyCopyrightOnSave(request.getKoreaCopyright(), request.getCopyrightNote(), username, Instant.now());

        if (fileChanged) {
            edition.changeFiles(request.getFileId(), request.getPreviewFileId());
            editionFileService.linkToEdition(edition.getId(), request.getFileId(), request.getPreviewFileId());
            // 파일을 뗀 판본이 추천이었다면 추천도 함께 푼다(§5-5 삭제와 같은 정리 — 02 §5-3).
            // 그러지 않으면 §5-6 이 400 으로 막는 "파일 없는 추천 판본" 이 저장돼 곡이 준비된 것처럼 보인다.
            // 다른 파일로 교체하는 경우(fileId 있음)는 파일이 계속 있으므로 추천을 유지한다.
            if (request.getFileId() == null && isRecommended(work, edition)) {
                work.clearRecommendation();
                workRepository.flush();
            }
            editionRepository.flush();
            editionFileService.deleteFiles(Arrays.asList(oldPdfFileId, oldPreviewFileId));
        } else if (!Objects.equals(oldPreviewFileId, request.getPreviewFileId())) {
            edition.changeFiles(request.getFileId(), request.getPreviewFileId());
            editionFileService.linkToEdition(edition.getId(), request.getFileId(), request.getPreviewFileId());
        }

        return toDetailDto(edition, work);
    }

    // ===== §5-4 조회 =====

    @Transactional(readOnly = true)
    public AdminEditionDTO get(Long editionId) {
        EditionEntity edition = findOrThrow(editionId);
        return toDetailDto(edition, edition.getWork());
    }

    // ===== §5-5 삭제 =====

    @Transactional
    public void delete(Long editionId) {
        EditionEntity edition = findOrThrow(editionId);
        WorkEntity work = edition.getWork();
        removeEditions(work, List.of(edition));
    }

    /** 곡 삭제(§4-9)·판본 삭제(§5-5) 공통 — 추천 해제 → 다운로드 기록 → 파일 → 판본 (01_ERD §7). */
    @Transactional
    public void removeEditions(WorkEntity work, List<EditionEntity> editions) {
        if (editions.isEmpty()) {
            return;
        }
        List<Long> editionIds = editions.stream().map(EditionEntity::getId).toList();
        if (work != null && work.getRecommendedEdition() != null
                && editionIds.contains(work.getRecommendedEdition().getId())) {
            work.clearRecommendation();
            workRepository.flush();
        }
        downloadLogRepository.deleteByEditionIds(editionIds);

        List<Long> fileIds = new ArrayList<>();
        for (EditionEntity edition : editions) {
            fileIds.add(edition.getPdfFileId());
            fileIds.add(edition.getPreviewFileId());
        }
        editionRepository.deleteAll(editions);
        editionRepository.flush();
        editionFileService.deleteFiles(fileIds);
    }

    // ===== §5-6 추천 지정 =====

    @Transactional
    public CopyrightDTOs.RecommendResult recommend(Long workId, Long editionId) {
        WorkEntity work = workRepository.findById(workId)
                .orElseThrow(() -> EntityNotFoundException.of("곡", workId));
        EditionEntity edition = editionRepository.findById(editionId)
                .filter(e -> e.getWork().getId().equals(workId))
                .orElseThrow(() -> EntityNotFoundException.of("판본", editionId));
        if (!edition.hasFile()) {
            throw new BusinessRuleException("파일이 없어 추천으로 지정할 수 없어요");
        }
        Long previous = work.getRecommendedEdition() == null ? null : work.getRecommendedEdition().getId();
        work.recommend(edition);
        return CopyrightDTOs.RecommendResult.builder()
                .workId(workId)
                .previousEditionId(previous)
                .editionId(editionId)
                .workStatus(work.status())
                .warning(edition.getKoreaCopyright() == KoreaCopyright.FREE ? null : "NOT_DOWNLOADABLE")
                .build();
    }

    // ===== §5-7 파일 받아오기 =====

    @Transactional
    public CopyrightDTOs.FetchFileResult requestFetch(Long editionId) {
        EditionEntity edition = findOrThrow(editionId);
        if (edition.hasFile()) {
            throw new BusinessRuleException("이미 파일이 있는 판본이에요");
        }
        if (edition.getImslpFileId() == null || edition.getImslpFileId().isBlank()) {
            throw new BusinessRuleException("IMSLP 파일 정보가 없어 받아올 수 없어요");
        }
        if (!LicenseCode.isRedistributable(edition.getImslpLicenseCode())) {
            throw new BusinessRuleException("재배포가 허용되지 않는 표기라 받아올 수 없어요");
        }
        if (edition.getFileFetchStatus() == FileFetchStatus.QUEUED
                || edition.getFileFetchStatus() == FileFetchStatus.FETCHING) {
            throw new DuplicateResourceException("이미 받아오는 중이에요");
        }
        edition.markFetchQueued();
        submitAfterCommit(editionId);
        return CopyrightDTOs.FetchFileResult.builder()
                .editionId(editionId)
                .fileFetchStatus(FileFetchStatus.QUEUED)
                .build();
    }

    private void submitAfterCommit(Long editionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    editionFileFetcher.fetch(editionId);
                }
            });
        } else {
            editionFileFetcher.fetch(editionId);
        }
    }

    // ===== §5-8 대기함 =====

    @Transactional(readOnly = true)
    public CopyrightDTOs.PendingListResult pending(String q, Long composerId, Pageable pageable) {
        Page<EditionEntity> page = editionRepository.findPending(SearchNormalizer.normalize(q), composerId, pageable);
        List<CopyrightDTOs.PendingEdition> content = new ArrayList<>();
        for (EditionEntity edition : page.getContent()) {
            WorkEntity work = edition.getWork();
            content.add(CopyrightDTOs.PendingEdition.builder()
                    .editionId(edition.getId())
                    .work(CopyrightDTOs.PendingEdition.Work.builder()
                            .id(work.getId())
                            .titleKo(work.getTitleKo())
                            .titleOriginal(work.getTitleOriginal())
                            .build())
                    .composer(CopyrightDTOs.PendingEdition.Composer.builder()
                            .id(work.getComposer().getId())
                            .nameKo(work.getComposer().getNameKo())
                            .nameOriginal(work.getComposer().getNameOriginal())
                            .deathYear(work.getComposer().getDeathYear())
                            .build())
                    .kind(edition.getKind())
                    .scope(edition.getScope())
                    .movementNumber(edition.getMovementNumber())
                    .editor(edition.getEditor())
                    .arranger(edition.getArranger())
                    .publisher(edition.getPublisher())
                    .publishYear(edition.getPublishYear())
                    .imslpCopyrightText(edition.getImslpCopyrightText())
                    .imslpFileUrl(edition.getImslpFileUrl())
                    .hasFile(edition.hasFile())
                    .build());
        }
        return CopyrightDTOs.PendingListResult.builder()
                .unfilteredTotal(editionRepository.countUnknownCopyright())
                .editions(PageResponse.<CopyrightDTOs.PendingEdition>builder()
                        .content(content)
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .first(page.isFirst())
                        .last(page.isLast())
                        .build())
                .build();
    }

    // ===== §5-9 / §5-10 판정 =====

    @Transactional
    public AdminEditionDTO judge(Long editionId, CopyrightDTOs.JudgeRequest request, String username) {
        requireNote(request.getKoreaCopyright(), request.getCopyrightNote());
        EditionEntity edition = findOrThrow(editionId);
        edition.judgeCopyright(request.getKoreaCopyright(), request.getCopyrightNote(), username, Instant.now());
        return toDetailDto(edition, edition.getWork());
    }

    @Transactional
    public CopyrightDTOs.BulkJudgeResult bulkJudge(CopyrightDTOs.BulkJudgeRequest request, String username) {
        requireNote(request.getKoreaCopyright(), request.getCopyrightNote());
        List<Long> succeeded = new ArrayList<>();
        List<CopyrightDTOs.BulkJudgeResult.Failure> failed = new ArrayList<>();
        for (Long editionId : request.getEditionIds()) {
            editionRepository.findById(editionId).ifPresentOrElse(edition -> {
                edition.judgeCopyright(request.getKoreaCopyright(), request.getCopyrightNote(),
                        username, Instant.now());
                succeeded.add(editionId);
            }, () -> failed.add(CopyrightDTOs.BulkJudgeResult.Failure.builder()
                    .editionId(editionId)
                    .reason("NOT_FOUND")
                    .build()));
        }
        return CopyrightDTOs.BulkJudgeResult.builder().succeeded(succeeded).failed(failed).build();
    }

    // ===== 내부 =====

    /** 이 판본이 곡의 추천 판본인가. */
    private boolean isRecommended(WorkEntity work, EditionEntity edition) {
        return work != null && work.getRecommendedEdition() != null
                && Objects.equals(work.getRecommendedEdition().getId(), edition.getId());
    }

    private EditionEntity findOrThrow(Long editionId) {
        return editionRepository.findById(editionId)
                .orElseThrow(() -> EntityNotFoundException.of("판본", editionId));
    }

    private AdminEditionDTO toDetailDto(EditionEntity edition, WorkEntity work) {
        List<EditionEntity> editions = editionRepository.findByWorkIdOrderByIdAsc(work.getId());
        if (editions.stream().noneMatch(e -> e.getId().equals(edition.getId()))) {
            editions = new ArrayList<>(editions);
            editions.add(edition);
        }
        Long recommendedId = work.getRecommendedEdition() == null ? null : work.getRecommendedEdition().getId();
        Long candidateId = EditionDtoAssembler.candidateOf(editions, recommendedId);
        Map<Long, FileEntity> files = editionDtoAssembler.loadFiles(List.of(edition));
        AdminEditionDTO dto = editionDtoAssembler.toAdminDto(edition, files, recommendedId, candidateId);
        dto.setWorkContext(work.getId(), work.status());
        return dto;
    }

    private void validate(EditionSaveDTO request) {
        if (request.getScope() == EditionScope.MOVEMENT
                && (request.getMovementNumber() == null || request.getMovementNumber() < 1)) {
            throw FieldValidationException.of("movementNumber", "악장 번호를 입력해 주세요", request.getMovementNumber());
        }
        requireNote(request.getKoreaCopyright(), request.getCopyrightNote());
        String fileUrl = request.getImslpFileUrl();
        if (fileUrl != null && !fileUrl.isBlank() && !ImslpUrlNormalizer.isImslpUrl(fileUrl)) {
            throw FieldValidationException.of("imslpFileUrl", "IMSLP 주소를 입력해 주세요", fileUrl);
        }
    }

    private void requireNote(KoreaCopyright koreaCopyright, String note) {
        if ((koreaCopyright == KoreaCopyright.FREE || koreaCopyright == KoreaCopyright.RESTRICTED)
                && (note == null || note.isBlank())) {
            throw FieldValidationException.of("copyrightNote", "판정 근거를 적어 주세요", note);
        }
    }
}
