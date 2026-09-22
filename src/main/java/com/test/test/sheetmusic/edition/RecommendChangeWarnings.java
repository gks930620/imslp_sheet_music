package com.test.test.sheetmusic.edition;

import com.test.test.sheetmusic.work.WorkStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * 추천 판본을 바꿀 때의 경고 6종 (02 §5-6·§5-6-2, 03 §30-3, 2026-09-21 신설).
 *
 * <p><b>예고(§5-6-2)와 결과(§5-6)가 같은 함수를 쓴다</b> — 규칙을 두 곳에 복사하지 않는다. 경고 ④⑤는 판본의
 * 성질이 아니라 <b>이 변경의 결과</b>라 곡의 지금 상태와 다운로드 기록 유무가 필요하고, 그래서 이 함수는
 * 판본 하나만으로 답할 수 없다(호출자가 그 값들을 <b>바뀌기 전</b> 상태로 넘겨야 한다).
 *
 * <p>고정 순서: {@code WORK_BECOMES_CLOSED → HAS_DOWNLOAD_HISTORY → NOT_DOWNLOADABLE → PARTS → ARRANGEMENT
 * → PARTIAL_SCOPE}(선언 순서 = 응답 순서). {@code PARTS}·{@code ARRANGEMENT} 는 {@code kind} 가 하나뿐이라
 * 동시에 성립할 수 없다.
 */
public final class RecommendChangeWarnings {

    private RecommendChangeWarnings() {
    }

    /**
     * @param workStatusBeforeChange 지정을 <b>적용하기 전</b> 그 곡의 상태 — 지정한 뒤에 읽으면 ④가 영영 뜨지 않는다
     * @param hasDownloadHistory     그 곡에 다운로드 기록이 있는가(§4-7 {@code hasDownloadHistory})
     * @param target                 지정하려는(또는 지정한) 판본
     */
    public static List<RecommendWarning> calculate(WorkStatus workStatusBeforeChange, boolean hasDownloadHistory,
                                                    EditionEntity target) {
        List<RecommendWarning> warnings = new ArrayList<>();
        boolean notFree = target.getKoreaCopyright() != KoreaCopyright.FREE;
        if (workStatusBeforeChange == WorkStatus.READY && notFree) {
            warnings.add(RecommendWarning.WORK_BECOMES_CLOSED);
        }
        if (hasDownloadHistory) {
            warnings.add(RecommendWarning.HAS_DOWNLOAD_HISTORY);
        }
        if (notFree) {
            warnings.add(RecommendWarning.NOT_DOWNLOADABLE);
        }
        if (target.getKind() == EditionKind.PARTS) {
            warnings.add(RecommendWarning.PARTS);
        }
        if (target.getKind() == EditionKind.ARRANGEMENT) {
            warnings.add(RecommendWarning.ARRANGEMENT);
        }
        if (target.getScope() == EditionScope.MOVEMENT) {
            warnings.add(RecommendWarning.PARTIAL_SCOPE);
        }
        return warnings;
    }
}
