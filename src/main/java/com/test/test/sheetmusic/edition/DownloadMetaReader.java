package com.test.test.sheetmusic.edition;

import com.test.test.common.exception.CopyrightRestrictedException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.common.exception.FileUnavailableException;
import com.test.test.file.entity.FileEntity;
import com.test.test.file.repository.FileRepository;
import com.test.test.sheetmusic.common.DownloadFileName;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import com.test.test.sheetmusic.work.WorkEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다운로드 메타 읽기 (02 §3-4) — <b>DB 작업만</b> 하는 짧은 읽기 트랜잭션.
 *
 * <p>저장소에서 바이트를 읽는 외부 I/O(S3 GetObject, 최대 100MB)는 여기 들어오지 않는다.
 * 트랜잭션이 열린 채 네트워크를 기다리면 커넥션 풀이 고갈되기 때문이다(컨벤션 §1).
 * 바이트 로드는 {@link DownloadService#prepare(Long)} 가 트랜잭션 밖에서 한다.
 *
 * <p>판정 순서는 계약이다: 숨김 곡 404 → 파일 없음 404 → 저작권 403 → files 행 없음 503.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DownloadMetaReader {

    private final EditionRepository editionRepository;
    private final FileRepository fileRepository;

    /** 다운로드에 필요한 메타 — 저장 파일명과 응답 파일명뿐이다(바이트는 담지 않는다). */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class DownloadMeta {
        private Long editionId;
        private String storedFileName;
        private String fileName;
    }

    DownloadMeta read(Long editionId) {
        EditionEntity edition = editionRepository.findById(editionId)
                .orElseThrow(() -> EntityNotFoundException.of("판본", editionId));
        WorkEntity work = edition.getWork();
        if (work.isHidden()) {
            throw EntityNotFoundException.of("판본", editionId);
        }
        if (edition.getPdfFileId() == null) {
            throw new EntityNotFoundException("파일이 없는 판본이에요");
        }
        if (edition.getKoreaCopyright() != KoreaCopyright.FREE) {
            throw new CopyrightRestrictedException("아직 받을 수 없는 판본이에요");
        }

        FileEntity file = fileRepository.findById(edition.getPdfFileId())
                .orElseThrow(() -> new FileUnavailableException("파일을 찾을 수 없어요"));

        return DownloadMeta.builder()
                .editionId(editionId)
                .storedFileName(file.getStoredFileName())
                .fileName(DownloadFileName.build(
                        work.getComposer().getNameKo(), work.getComposer().getNameOriginal(),
                        work.getTitleKo(), work.getTitleOriginal(), work.primaryCatalogNumber(),
                        edition.downloadNameSuffix()))
                .build();
    }
}
