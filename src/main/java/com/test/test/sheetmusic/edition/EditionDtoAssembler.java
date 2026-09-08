package com.test.test.sheetmusic.edition;

import com.test.test.file.entity.FileEntity;
import com.test.test.file.repository.FileRepository;
import com.test.test.sheetmusic.edition.dto.AdminEditionDTO;
import com.test.test.sheetmusic.edition.dto.EditionDTO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 판본 엔티티 → 응답 DTO 조립 (02 §2-3, §4-7).
 * 파일 크기·미리보기 URL 은 기존 {@code files} 테이블에 있으므로 id 목록으로 한 번에 읽어 N+1 을 피한다.
 */
@Component
@RequiredArgsConstructor
public class EditionDtoAssembler {

    /**
     * 판본 정렬의 공통 순서 (02 §3-3 · §3-3-2 · §4-7): {@code imslp_download_count DESC NULLS LAST, id ASC}.
     * "다른 판본" 목록·IMSLP 후보·관리 목록이 같은 순서를 쓰므로 한 곳에 둔다.
     */
    public static final Comparator<EditionEntity> BY_IMSLP_DOWNLOADS =
            Comparator.comparingInt(EditionDtoAssembler::downloads).reversed()
                    .thenComparing(EditionEntity::getId);

    private final FileRepository fileRepository;

    /** 판본들이 참조하는 files 행을 id → 엔티티로 모아 온다. */
    public Map<Long, FileEntity> loadFiles(Collection<EditionEntity> editions) {
        List<Long> ids = new ArrayList<>();
        for (EditionEntity edition : editions) {
            if (edition.getPdfFileId() != null) {
                ids.add(edition.getPdfFileId());
            }
            if (edition.getPreviewFileId() != null) {
                ids.add(edition.getPreviewFileId());
            }
        }
        Map<Long, FileEntity> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (FileEntity file : fileRepository.findAllById(ids)) {
            map.put(file.getId(), file);
        }
        return map;
    }

    public EditionDTO toDto(EditionEntity edition, Map<Long, FileEntity> files) {
        Long fileSize = fileSize(edition, files);
        boolean downloadable = edition.isDownloadable();
        return EditionDTO.builder()
                .id(edition.getId())
                .kind(edition.getKind())
                .scope(edition.getScope())
                .movementNumber(edition.getMovementNumber())
                .sectionLabel(edition.getSectionLabel())
                .pageCount(edition.getPageCount())
                .fileSize(fileSize)
                .hasFile(edition.hasFile())
                .previewUrl(publicPreviewUrl(edition, files))
                .publisher(edition.getPublisher())
                .publishYear(edition.getPublishYear())
                .plateNumber(edition.getPlateNumber())
                .editor(edition.getEditor())
                .arranger(edition.getArranger())
                .scanner(edition.getScanner())
                .koreaCopyright(edition.getKoreaCopyright())
                .imslpCopyrightText(edition.getImslpCopyrightText())
                .ccLicenseName(edition.getCcLicenseName())
                .ccAttribution(edition.getCcAttribution())
                .imslpFileUrl(edition.getImslpFileUrl())
                .downloadable(downloadable)
                .largeFile(isLargeFile(fileSize))
                .downloadUrl(downloadUrl(edition, downloadable))
                .build();
    }

    public AdminEditionDTO toAdminDto(EditionEntity edition, Map<Long, FileEntity> files,
                                      Long recommendedEditionId, Long candidateEditionId) {
        Long fileSize = fileSize(edition, files);
        boolean downloadable = edition.isDownloadable();
        return AdminEditionDTO.builder()
                .id(edition.getId())
                .kind(edition.getKind())
                .scope(edition.getScope())
                .movementNumber(edition.getMovementNumber())
                .sectionLabel(edition.getSectionLabel())
                .pageCount(edition.getPageCount())
                .fileSize(fileSize)
                .hasFile(edition.hasFile())
                .previewUrl(previewUrl(edition, files))
                .publisher(edition.getPublisher())
                .publishYear(edition.getPublishYear())
                .plateNumber(edition.getPlateNumber())
                .editor(edition.getEditor())
                .arranger(edition.getArranger())
                .scanner(edition.getScanner())
                .koreaCopyright(edition.getKoreaCopyright())
                .imslpCopyrightText(edition.getImslpCopyrightText())
                .ccLicenseName(edition.getCcLicenseName())
                .ccAttribution(edition.getCcAttribution())
                .imslpFileUrl(edition.getImslpFileUrl())
                .downloadable(downloadable)
                .largeFile(isLargeFile(fileSize))
                .downloadUrl(downloadUrl(edition, downloadable))
                .imslpFileId(edition.getImslpFileId())
                .imslpOriginalFileName(edition.getImslpOriginalFileName())
                .imslpDescription(edition.getImslpDescription())
                .imslpLicenseCode(edition.getImslpLicenseCode())
                .imslpDownloadCount(edition.getImslpDownloadCount())
                .pdfFileId(edition.getPdfFileId())
                .previewFileId(edition.getPreviewFileId())
                .copyrightNote(edition.getCopyrightNote())
                .copyrightJudgedAt(edition.getCopyrightJudgedAt())
                .copyrightJudgedBy(edition.getCopyrightJudgedBy())
                .fileFetchStatus(edition.getFileFetchStatus())
                .fileFetchError(edition.getFileFetchError())
                .fileFetchedAt(edition.getFileFetchedAt())
                .downloadCount(edition.getDownloadCount())
                .isRecommended(edition.getId().equals(recommendedEditionId))
                .isCandidate(edition.getId().equals(candidateEditionId))
                .createdAt(edition.getCreatedAt())
                .updatedAt(edition.getUpdatedAt())
                .build();
    }

    private Long fileSize(EditionEntity edition, Map<Long, FileEntity> files) {
        FileEntity pdf = edition.getPdfFileId() == null ? null : files.get(edition.getPdfFileId());
        return pdf == null ? null : pdf.getFileSize();
    }

    /**
     * 공개 응답의 미리보기 (02 §2-3 · §0-4, 2026-09-08 / 기획 §F3-6) — <b>판정이 FREE 인 판본만</b> 값이 있다.
     *
     * <p>다운로드를 막는 이유는 1쪽 이미지에도 그대로 적용된다. {@code previewUrl == null} 의 두 이유(파일 없음 ·
     * 판정 안 끝남)는 화면이 {@code koreaCopyright} 로 가르므로 별도 필드를 두지 않는다.
     * 관리 응답({@link #toAdminDto})은 판정과 무관하게 그대로다 — 판정 근거가 미리보기 그 자체이기 때문이다(기획 §F6-4).
     * 응답만 막으면 저장 파일명을 아는 사람에게는 열려 있으므로 서빙 게이트가 함께 간다
     * ({@code FileService.isPubliclyServable} — 02 §0-4 표).
     */
    public String publicPreviewUrl(EditionEntity edition, Map<Long, FileEntity> files) {
        return edition.getKoreaCopyright() == KoreaCopyright.FREE ? previewUrl(edition, files) : null;
    }

    private String previewUrl(EditionEntity edition, Map<Long, FileEntity> files) {
        FileEntity preview = edition.getPreviewFileId() == null ? null : files.get(edition.getPreviewFileId());
        return preview == null ? null : preview.getFilePath();
    }

    private static boolean isLargeFile(Long fileSize) {
        return fileSize != null && fileSize >= EditionDTO.LARGE_FILE_BYTES;
    }

    private static String downloadUrl(EditionEntity edition, boolean downloadable) {
        return downloadable ? "/api/editions/" + edition.getId() + "/download" : null;
    }

    /**
     * 추천 후보 (01_ERD §3-3): 전체 악보·전곡·파일 있음 중 IMSLP 다운로드 수 최대(동률이면 id 최소).
     * 추천 판본이 이미 있으면 후보를 표시하지 않는다.
     */
    public static Long candidateOf(List<EditionEntity> editions, Long recommendedEditionId) {
        if (recommendedEditionId != null) {
            return null;
        }
        EditionEntity best = null;
        for (EditionEntity edition : editions) {
            if (!edition.isCandidateEligible()) {
                continue;
            }
            if (best == null || downloads(edition) > downloads(best)
                    || (downloads(edition) == downloads(best) && edition.getId() < best.getId())) {
                best = edition;
            }
        }
        return best == null ? null : best.getId();
    }

    /**
     * 추천 없는 곡이 내보내는 IMSLP 파일 페이지 후보 (02 §3-3-2, 기획 §F3-7 · §10-5).
     *
     * <p>{@code kind = COMPLETE_SCORE} · {@code scope = COMPLETE} 인 판본 중
     * {@code imslp_download_count DESC NULLS LAST, id ASC} 첫 번째. <b>파일 유무는 보지 않는다</b> —
     * {@link #candidateOf} (01_ERD §3-3 추천 후보)에서 파일 조건만 뺀 규칙이고, 준비 중 곡에는 파일이 없기 때문이다.
     * 편곡·발췌를 "우리가 고른 판본" 이라며 내보내지 않으므로 자격이 없으면 null 이고, 화면은 그때만 작품 페이지로 폴백한다.
     *
     * @param editions 추천을 뺀 판본들 — 정렬은 이 메서드가 하지 않고 {@link #BY_IMSLP_DOWNLOADS} 로 이미 정렬된 순서를 쓴다
     */
    public static EditionEntity imslpCandidateOf(List<EditionEntity> editions) {
        EditionEntity best = null;
        for (EditionEntity edition : editions) {
            if (!edition.isImslpCandidateEligible()) {
                continue;
            }
            if (best == null || downloads(edition) > downloads(best)
                    || (downloads(edition) == downloads(best) && edition.getId() < best.getId())) {
                best = edition;
            }
        }
        return best;
    }

    /**
     * 관리 화면 판본 정렬 (02 §4-7): 추천 → 후보 → 파일 있음 → 파일 없음, 각 구간은 IMSLP 다운로드 수 DESC → id ASC.
     */
    public static List<EditionEntity> sortForAdmin(List<EditionEntity> editions,
                                                   Long recommendedEditionId, Long candidateEditionId) {
        List<EditionEntity> sorted = new ArrayList<>(editions);
        sorted.sort(Comparator
                .comparingInt((EditionEntity e) -> group(e, recommendedEditionId, candidateEditionId))
                .thenComparing(BY_IMSLP_DOWNLOADS));
        return sorted;
    }

    private static int downloads(EditionEntity edition) {
        return edition.getImslpDownloadCount() == null ? Integer.MIN_VALUE : edition.getImslpDownloadCount();
    }

    private static int group(EditionEntity edition, Long recommendedEditionId, Long candidateEditionId) {
        if (edition.getId().equals(recommendedEditionId)) {
            return 0;
        }
        if (edition.getId().equals(candidateEditionId)) {
            return 1;
        }
        return edition.hasFile() ? 2 : 3;
    }
}
