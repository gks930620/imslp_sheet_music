package com.test.test.sheetmusic.edition;

/**
 * 저작권 자동 판정 규칙 (기획 {@code 02_저작권_판정_지침.md} 부록 A §A-1, 02_API_명세서 §5-11).
 *
 * <p><b>순수 함수다.</b> 저장소·시계·스프링에 의존하지 않고 값만 받아 값을 돌려준다. 그래서
 * <ul>
 *   <li>§5-8 대기함의 {@code autoJudgeSkipReason}(왜 자동으로 안 열렸는지)과 §5-11 실제 판정이
 *       <b>같은 함수</b>를 쓴다 — "대기함엔 막혔다는데 실행하면 열린다" 같은 어긋남이 생길 수 없다.</li>
 *   <li>{@code currentYear} 를 파라미터로 받는다(컨벤션 §6: 시간 의존 로직은 시간을 주입).
 *       기준 연도(올해 − 70 / 올해 − 120)를 상수로 박지 않으므로 해가 바뀌면 판정도 함께 움직인다.</li>
 * </ul>
 *
 * <p>자동 판정은 {@code UNKNOWN → FREE} <b>한 방향</b>으로만 움직인다. {@code RESTRICTED} 는 만들지 않는다
 * (부록 A §A-2 ⑤ — 단정의 근거가 우리에게 없고, 대기함에서 사라져 사람이 다시 볼 기회를 잃는다).
 */
public final class CopyrightAutoJudge {

    /**
     * 자동 판정한 판본의 {@code copyright_judged_by} (02 §5-11·§5-12).
     * 사람 username 과 구분되는 센티널 — username 에는 {@code :} 를 쓸 수 없어 충돌하지 않는다.
     * 쓰기(§5-11)·되돌리기(§5-12)·화면 표시가 모두 이 상수 하나를 본다.
     */
    public static final String AUTO_JUDGED_BY = "system:auto";

    /** 저작권 보호기간 — 사망/공표 후 70년(한국 저작권법). */
    private static final int PROTECTION_YEARS = 70;
    /**
     * 편집자 사망 연도의 대용 (부록 A §A-2 ④): 편집 시 나이 하한 25세 + 사망 나이 상한 75세 → 사망 ≤ 출판 + 50년,
     * 보호 만료 = 사망 + 70년 ≤ 출판 + 120년. 통계적 안전선이지 법적 보증이 아니라, 그래서 되돌릴 수 있게 만든다.
     */
    private static final int EDITOR_PROXY_YEARS = 120;

    private CopyrightAutoJudge() {
    }

    /** 자동으로 {@code FREE} 를 만든 근거 (02 §5-11 {@code byRule} 고정 순서). */
    public enum Rule {
        CC_REDISTRIBUTABLE, PD_NO_EDITOR, PD_OLD_PUBLICATION
    }

    /** {@code UNKNOWN} 으로 남긴 사유 (부록 A §A-1 표 순서 = 02 §5-11 {@code skipped} 고정 순서). */
    public enum SkipReason {
        LICENSE_NOT_REDISTRIBUTABLE, COMPOSER_DEATH_YEAR_UNKNOWN, COMPOSER_COPYRIGHT_ACTIVE,
        PUBLICATION_TOO_RECENT, EDITOR_UNVERIFIABLE
    }

    /**
     * 부록 A §A-1 표를 <b>위에서부터</b> 적용해 먼저 걸리는 곳에서 끝낸다.
     *
     * @param licenseCode       IMSLP 표기의 정규화 코드({@code null} 가능 = 표기 없음)
     * @param composerDeathYear 작곡가 사망 연도({@code null} 가능 = 모름)
     * @param editor            편집자 표기
     * @param arranger          편곡자 표기
     * @param kind              판본 종류 — {@code ARRANGEMENT} 는 편집자 표기가 있는 것과 같이 다룬다
     * @param publishYear       그 스캔본의 출판 연도({@code null} 가능 = {@code n.d.})
     * @param currentYear       기준 연도(서비스가 {@code app.timezone} 기준 올해를 넘긴다)
     */
    public static Verdict judge(LicenseCode licenseCode, Integer composerDeathYear, String editor, String arranger,
                                EditionKind kind, Integer publishYear, int currentYear) {
        // 1. 재배포가 허용되는 표기가 아니면(표기 없음 포함) 아무것도 하지 않는다.
        //    "IMSLP 에 있으면 저작권이 없다"는 사실이 아니다 — CC-BY-NC/ND 같은 조건부 판본이 섞여 있다.
        if (!LicenseCode.isRedistributable(licenseCode)) {
            return Verdict.skip(SkipReason.LICENSE_NOT_REDISTRIBUTABLE);
        }
        // 2·3. 작곡가 조건은 모든 분기의 전제다. CC 라이선스는 조판·편집한 사람의 권리만 풀기 때문에,
        //      작품 자체가 보호 중이면 라이선스와 무관하게 우리가 배포할 수 없다.
        if (composerDeathYear == null) {
            return Verdict.skip(SkipReason.COMPOSER_DEATH_YEAR_UNKNOWN);
        }
        if (composerDeathYear + PROTECTION_YEARS >= currentYear) {
            return Verdict.skip(SkipReason.COMPOSER_COPYRIGHT_ACTIVE);
        }
        // 4. 저작권자가 재배포를 명시적으로 허락했으므로 편집자 사망 연도를 따질 필요가 없다.
        if (licenseCode != LicenseCode.PD) {
            return Verdict.free(Rule.CC_REDISTRIBUTABLE,
                    "자동 판정: 재배포 허용 라이선스(%s) + 작곡가 %d년 사망"
                            .formatted(licenseCode.name(), composerDeathYear));
        }
        // 5·6. 편집자·편곡자 표기가 없는 옛 악보의 스캔은 창작성 있는 선택·배열이 없어 새 저작권이 생기지 않는다.
        //      그래서 출판 연도가 없어도(n.d.) 통과시킨다. 다만 출판이 최근이면 "표기가 누락된 새 조판"일 수 있어 막는다.
        if (hasNoEditorialWork(editor, arranger, kind)) {
            if (publishYear == null || publishYear + PROTECTION_YEARS < currentYear) {
                return Verdict.free(Rule.PD_NO_EDITOR,
                        "자동 판정: Public Domain + 작곡가 %d년 사망 + 편집자 표기 없음".formatted(composerDeathYear));
            }
            return Verdict.skip(SkipReason.PUBLICATION_TOO_RECENT);
        }
        // 7. 편집자 표기가 있으면 사망 연도를 모르므로 출판 + 120년을 만료 시점의 상한으로 쓴다.
        if (publishYear != null && publishYear + EDITOR_PROXY_YEARS < currentYear) {
            return Verdict.free(Rule.PD_OLD_PUBLICATION,
                    "자동 판정: Public Domain + 작곡가 %d년 사망 + %d년 출판(%d년 경과)"
                            .formatted(composerDeathYear, publishYear, EDITOR_PROXY_YEARS));
        }
        // 8. 편집자 표기 있음 + 출판 120년 미경과(또는 출판 연도 없음) → 사람이 본다.
        return Verdict.skip(SkipReason.EDITOR_UNVERIFIABLE);
    }

    /** 새로 보호될 편집·편곡 작업이 없는가. */
    private static boolean hasNoEditorialWork(String editor, String arranger, EditionKind kind) {
        return isBlank(editor) && isBlank(arranger) && kind != EditionKind.ARRANGEMENT;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 판정 결과 — {@code FREE + 규칙 + 근거 문구} 이거나 {@code UNKNOWN 유지 + 사유}. 둘 중 하나만 채워진다. */
    public static final class Verdict {

        private final Rule rule;
        private final SkipReason skipReason;
        private final String note;

        private Verdict(Rule rule, SkipReason skipReason, String note) {
            this.rule = rule;
            this.skipReason = skipReason;
            this.note = note;
        }

        private static Verdict free(Rule rule, String note) {
            return new Verdict(rule, null, note);
        }

        private static Verdict skip(SkipReason skipReason) {
            return new Verdict(null, skipReason, null);
        }

        /** 자동으로 열 수 있는 판본인가. */
        public boolean isFree() {
            return rule != null;
        }

        public Rule getRule() {
            return rule;
        }

        /** 자동으로 열리지 않은 사유. 열 수 있으면 {@code null} (§5-8 이 그대로 내려보낸다). */
        public SkipReason getSkipReason() {
            return skipReason;
        }

        /** {@code copyright_note} 에 쓸 고정 문구 — 이 문자열이 계약이다(02 §5-11 표). */
        public String getNote() {
            return note;
        }
    }
}
