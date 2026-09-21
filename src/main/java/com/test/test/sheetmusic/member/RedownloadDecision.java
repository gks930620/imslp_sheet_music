package com.test.test.sheetmusic.member;

import com.test.test.sheetmusic.edition.EditionDtoAssembler;
import com.test.test.sheetmusic.edition.EditionEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 다시 받기 판정 (02 §10-3) — <b>이 한 함수</b>가 상태·버튼 주소·대체 판본을 함께 정한다.
 * 세 값을 서로 다른 자리에서 계산하면 화면이 말하는 것과 버튼이 여는 것이 갈린다.
 *
 * <pre>
 * receivedDownloadable  = 받은 판본이 살아 있고 파일이 있고 판정이 FREE   (= edition.isDownloadable())
 * ① receivedDownloadable && 받은 판본 == 지금 추천  → AVAILABLE               (버튼: 그때 판본)
 * ② receivedDownloadable                           → RECOMMENDATION_CHANGED  (버튼: 그때 판본)
 * ③ 그 밖                                          → UNAVAILABLE
 *      ├ 지금 추천을 받을 수 있으면 → 버튼·대체 모두 지금 추천 판본      (③-a)
 *      └ 아니면                     → 버튼 없음, 대체 없음               (③-b)
 * </pre>
 *
 * <p>"이용 제한" 판본은 어떤 경로로도 주소가 만들어지지 않는다(기획 05 §3-3) — 게이트는 두 겹이되
 * 판정 규칙은 {@link EditionEntity#isDownloadable()} 한 곳이다(02 §3-4 와 같은 값).
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedownloadDecision {

    private final RedownloadState state;

    /** "다시 받기" 버튼이 실제로 여는 판본. null 이면 버튼이 없다. */
    private final EditionEntity target;

    /** "지금 추천 판본: …" 줄 — ③-a 일 때만 값이 있다. */
    private final EditionEntity alternative;

    public static RedownloadDecision of(EditionEntity received, EditionEntity recommended) {
        boolean receivedDownloadable = received != null && received.isDownloadable();
        if (receivedDownloadable) {
            boolean sameAsRecommended = recommended != null && recommended.getId().equals(received.getId());
            return new RedownloadDecision(
                    sameAsRecommended ? RedownloadState.AVAILABLE : RedownloadState.RECOMMENDATION_CHANGED,
                    received, null);
        }
        if (recommended != null && recommended.isDownloadable()) {
            return new RedownloadDecision(RedownloadState.UNAVAILABLE, recommended, recommended);
        }
        return new RedownloadDecision(RedownloadState.UNAVAILABLE, null, null);
    }

    public String downloadUrl() {
        return this.target == null ? null : EditionDtoAssembler.downloadUrl(this.target);
    }
}
