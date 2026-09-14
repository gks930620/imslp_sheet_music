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
 *
 * <p><b>조건은 둘의 AND 다</b> (02 §0-4, 2026-09-09 — qa 5차 결함 3): 판정이 {@code FREE} 이고
 * <b>그 곡이 숨김이 아니어야</b> 공개다. 숨김의 정의가 "사용자 화면 어디에도 안 나온다"(기획 §F2-6)인데
 * 곡 상세(§3-3)·다운로드(§3-4)가 404 인 곡의 1쪽 이미지만 열려 있으면 숨긴 것이 아니다.
 * 특히 수집이 "피아노 독주곡이 아닌 것 같아요" 로 자동 숨김한 곡({@code hidden_reason = NOT_PIANO_SOLO})은
 * <b>우리가 아직 무엇인지 판단하지 못한 곡</b>이라, 판정이 안 끝난 판본을 감추는 것과 같은 결정이다.
 *
 * <p>ADMIN 우회는 이 게이트가 아니라 호출자({@code FileService})에 있다 — 숨김을 풀지 말지,
 * 판정을 무엇으로 할지의 근거가 그 1쪽 이미지 자체이기 때문이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CopyrightEditionPreviewGate implements EditionPreviewGate {

    private final EditionRepository editionRepository;

    @Override
    public boolean isPreviewOpenToPublic(long editionId) {
        return editionRepository.findById(editionId)
                .map(edition -> edition.getKoreaCopyright() == KoreaCopyright.FREE
                        && !edition.getWork().isHidden())
                .orElse(false);
    }
}
