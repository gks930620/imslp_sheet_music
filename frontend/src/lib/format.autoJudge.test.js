import { formatAutoJudgeSkipReason } from "./format.js";

/**
 * 자동 판정 미적용 사유 문구 — 02_API_명세서 §5-8 `autoJudgeSkipReason` · §5-11 `skipped[].reason`,
 * 기획 `02_저작권_판정_지침.md` 부록 A §A-1.
 *
 * <p><b>문구는 senior-dev 가 확정했다(2026-09-08).</b> designer 로 넘기지 않은 이유는 새 문구를 짓는 일이
 * 아니기 때문이다 — 같은 5개 코드의 한국어 문구가 이미 대기함 <b>같은 화면</b>의 자동 판정 미리보기 모달
 * (§5-11 `skipped`)에 나가고 있고, 계약 문서 §7 도 예시로 `EDITOR_UNVERIFIABLE → "편집자 생몰 확인 필요"` 를
 * 적어 두었다. 한 화면에서 같은 코드가 두 가지 말로 보이면 관리자는 그 둘이 같은 것인지 알 수 없다.
 *
 * <p>그래서 이 테스트가 잠그는 것은 <b>문구의 단일 출처</b>다. 지금 문구 표는 `AutoJudgePanel.jsx` 안에만
 * 있어서(SKIP_LABELS) 행 표시가 표를 한 벌 더 만들면 그 순간 두 벌이 되고, 다음에 한쪽만 고쳐진다.
 * enum → 문구 변환은 이 프로젝트에서 `lib/format.js` 가 맡는다(formatWorkStatus·formatCrawlJobStatus·
 * formatRecommendWarning 과 같은 자리).
 *
 * <p>designer 에게 남은 것은 문구가 아니라 <b>행에서의 표현</b>(자리·색·아이콘)이다.
 */
const PHRASES = [
  ["LICENSE_NOT_REDISTRIBUTABLE", "재배포 허용 라이선스가 아님"],
  ["COMPOSER_DEATH_YEAR_UNKNOWN", "작곡가 몰년을 모름"],
  ["COMPOSER_COPYRIGHT_ACTIVE", "작곡가 사후 70년 미경과"],
  ["PUBLICATION_TOO_RECENT", "출판 70년 미경과"],
  ["EDITOR_UNVERIFIABLE", "편집자 생몰 확인 필요"],
];

describe("formatAutoJudgeSkipReason (자동 판정이 열지 못한 이유)", () => {
  it.each(PHRASES)("%s → '%s'", (code, phrase) => {
    expect(formatAutoJudgeSkipReason(code)).toBe(phrase);
  });

  it("null·undefined 는 빈 문자열 — 자동 판정으로 열릴 판본이라 할 말이 없다", () => {
    expect(formatAutoJudgeSkipReason(null)).toBe("");
    expect(formatAutoJudgeSkipReason(undefined)).toBe("");
  });

  it("모르는 코드는 코드를 그대로 돌려준다 — 새 사유가 생겨도 화면에서 사라지지 않는다", () => {
    // 빈 문자열로 삼키면 관리자의 일감 하나가 아무 설명 없이 조용히 없어진다.
    // AutoJudgePanel 의 미리보기 모달도 같은 폴백(`labels[code] ?? code`)을 쓰고 있다.
    expect(formatAutoJudgeSkipReason("SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });
});
