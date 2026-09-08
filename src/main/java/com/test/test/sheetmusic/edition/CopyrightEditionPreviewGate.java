package com.test.test.sheetmusic.edition;

import com.test.test.file.service.EditionPreviewGate;
import com.test.test.sheetmusic.edition.repository.EditionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EditionPreviewGate} 구현 (02 §0-4 · §2-3, 기획 §F3-6).
 *
 * <p>다운로드를 막는 이유는 그 판본의 <b>1쪽 이미지에도 그대로 적용된다</b> — 재배포 책임은
 * 우리 도메인이 그 바이트를 주느냐로 정해지지, 우리 JSON 이 주소를 알려줬느냐로 정해지지 않는다
 * (기획 {@code 02_저작권_판정_지침} A-4). 그래서 공개 응답의 {@code previewUrl} 을 비우는 것과
 * 같은 조건을 서빙에도 건다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CopyrightEditionPreviewGate implements EditionPreviewGate {

    private final EditionRepository editionRepository;

    @Override
    public boolean isPreviewOpenToPublic(long editionId) {
        return editionRepository.findById(editionId)
                .map(edition -> edition.getKoreaCopyright() == KoreaCopyright.FREE)
                .orElse(false);
    }
}
