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
     * 관리 화면 판본 정렬 (02 §4-7): 추천 → 후보 → 파일 있음 → 파일 없음, 각 구간은 IMSLP 다운로드 수 DESC → id ASC.
     */
    public static List<EditionEntity> sortForAdmin(List<EditionEntity> editions,
                                                   Long recommendedEditionId, Long candidateEditionId) {
        List<EditionEntity> sorted = new ArrayList<>(editions);
        sorted.sort(Comparator
                .comparingInt((EditionEntity e) -> group(e, recommendedEditionId, candidateEditionId))
                .thenComparing(Comparator.comparingInt(EditionDtoAssembler::downloads).reversed())
                .thenComparing(EditionEntity::getId));
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
