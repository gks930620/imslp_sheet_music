package com.test.test.sheetmusic.edition.dto;

import com.test.test.sheetmusic.edition.CopyrightAutoJudge;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 저작권 자동 판정·되돌리기 DTO (02 §5-11·§5-12). */
public final class AutoJudgeDTOs {

    private AutoJudgeDTOs() {
    }

    /**
     * §5-11 요청. 본문 전체가 생략 가능하고 두 필드도 선택이라, 기본값을 필드 초기값으로 둔다
     * (JSON 에 없으면 Jackson 이 건드리지 않으므로 이 값이 그대로 쓰인다).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoJudgeRequest {

        /** {@code true} 면 아무것도 저장하지 않고 같은 숫자만 계산한다(실행 전 미리보기). */
        private boolean dryRun = false;

        /** 판정 후 추천 판본이 없는 곡에 추천을 자동 지정한다 — 끄면 곡이 계속 PREPARING 이라 다운로드가 0이다. */
        private boolean assignRecommended = true;
    }

    /** §5-11 응답. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoJudgeResult {

        private boolean dryRun;
        /** 실행 <b>전</b> UNKNOWN 판본 수. 항상 {@code judgedFree + remainingUnknown} 과 같다. */
        private int targetCount;
        private int judgedFree;
        private int remainingUnknown;
        private int recommendedAssigned;
        /** 규칙 3개 고정 순서 — count 가 0 이어도 빼지 않는다(화면이 항목 유무로 분기하지 않게). */
        private List<RuleCount> byRule;
        /** 사유 5개 고정 순서 — 마찬가지로 count 0 도 남긴다. */
        private List<SkipCount> skipped;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RuleCount {
            private CopyrightAutoJudge.Rule rule;
            private int count;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SkipCount {
            private CopyrightAutoJudge.SkipReason reason;
            private int count;
        }
    }

    /** §5-12 응답. 되돌릴 것이 없어도 200 {@code { "reverted": 0, "recommendationKept": 0 }} 이다(에러가 아니다). */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UndoResult {
        private int reverted;
        /**
         * 되돌린 판본 중 어떤 곡의 추천 판본으로 지정돼 있던 것의 수 = 이번 되돌리기로 <b>다운로드가 닫힌 곡 수</b>.
         * 화면은 이 숫자로 "추천 지정은 그대로 뒀어요 — N곡은 다시 판정해야 열려요" 를 만든다(02 §5-12).
         */
        private int recommendationKept;
    }
}
