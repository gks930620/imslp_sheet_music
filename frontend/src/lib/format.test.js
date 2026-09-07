import {
  formatFileSize,
  formatFileSizeCompact,
  formatLevel,
  formatWorkStatus,
  formatCopyright,
  formatEditionKind,
  formatEditionScope,
  formatLifeSpan,
  formatEstimatedTime,
  formatElapsedTime,
  formatCrawlJobStatus,
} from "./format.js";

// 00_공통_레이아웃_토큰.md §3-3(난이도), §3-4(뱃지), §4(숫자 표기), 04(생몰년), 07(예상 소요·걸린 시간)

describe("formatFileSize (기존 — 커뮤니티 첨부용, 동작 유지)", () => {
  it.each([
    [0, "0 B"],
    [null, "0 B"],
    [512, "512 B"],
    [1536, "1.5 KB"],
    [1048576, "1.0 MB"],
  ])("%s → %s", (input, expected) => {
    expect(formatFileSize(input)).toBe(expected);
  });
});

describe("formatFileSizeCompact — 악보 화면 표기 (소수 1자리, 단위 붙여 씀, 1MB 미만은 KB)", () => {
  it.each([
    [2516582, "2.4MB"],
    [1059957, "1.0MB"],
    [26214400, "25.0MB"],
    [655360, "640KB"],
    [1024, "1KB"],
    [512, "1KB"],
  ])("%s → %s", (input, expected) => {
    expect(formatFileSizeCompact(input)).toBe(expected);
  });

  it("값이 없으면 빈 문자열", () => {
    expect(formatFileSizeCompact(null)).toBe("");
    expect(formatFileSizeCompact(undefined)).toBe("");
  });
});

describe("formatLevel — 난이도 칩 문구", () => {
  it.each([
    ["BEGINNER", "입문"],
    ["ELEMENTARY", "초급"],
    ["INTERMEDIATE", "중급"],
    ["ADVANCED", "고급"],
    [null, "난이도 미정"],
    [undefined, "난이도 미정"],
  ])("%s → %s", (input, expected) => {
    expect(formatLevel(input)).toBe(expected);
  });
});

describe("formatWorkStatus — 곡 상태 뱃지 문구 (READY 는 뱃지 없음 → null)", () => {
  it.each([
    ["READY", null],
    ["PREPARING", "준비 중"],
    ["RESTRICTED", "한국에서 이용 제한"],
    ["UNKNOWN", "저작권 확인 중"],
  ])("%s → %s", (input, expected) => {
    expect(formatWorkStatus(input)).toBe(expected);
  });
});

describe("formatCopyright — 판본 저작권 뱃지 문구", () => {
  it.each([
    ["FREE", "한국에서 자유 이용 가능"],
    ["RESTRICTED", "한국에서 이용 제한"],
    ["UNKNOWN", "저작권 확인 중"],
  ])("%s → %s", (input, expected) => {
    expect(formatCopyright(input)).toBe(expected);
  });
});

describe("formatEditionKind / formatEditionScope — 판본 종류·포함 범위 (원어 금지)", () => {
  it.each([
    ["COMPLETE_SCORE", "전체 악보"],
    ["PARTS", "파트보"],
    ["ARRANGEMENT", "편곡"],
  ])("kind %s → %s", (input, expected) => {
    expect(formatEditionKind(input)).toBe(expected);
  });

  it("전곡", () => {
    expect(formatEditionScope({ scope: "COMPLETE", movementNumber: null, sectionLabel: null })).toBe("전곡");
  });

  it("특정 악장 → 'N악장만'", () => {
    expect(formatEditionScope({ scope: "MOVEMENT", movementNumber: 2, sectionLabel: null })).toBe("2악장만");
  });

  it("악장 번호가 없으면 IMSLP 섹션명으로 폴백", () => {
    expect(formatEditionScope({ scope: "MOVEMENT", movementNumber: null, sectionLabel: "Adagio sostenuto" })).toBe("Adagio sostenuto");
  });
});

describe("formatLifeSpan — 생몰년 (en dash)", () => {
  it.each([
    [1843, 1907, "1843–1907"],
    [1843, null, "1843–"],
    [null, null, ""],
  ])("%s, %s → %s", (birth, death, expected) => {
    expect(formatLifeSpan(birth, death)).toBe(expected);
  });
});

describe("formatEstimatedTime — 예상 소요 (07-A 요약 줄)", () => {
  it.each([
    [30, "1분 이내"],
    [59, "1분 이내"],
    [720, "약 12분"],
    [3600, "약 1시간"],
    [4200, "약 1시간 10분"],
  ])("%s초 → %s", (input, expected) => {
    expect(formatEstimatedTime(input)).toBe(expected);
  });

  it("값이 없으면 null (화면이 '예상 소요 계산 중' 으로 대체)", () => {
    expect(formatEstimatedTime(null)).toBeNull();
    expect(formatEstimatedTime(undefined)).toBeNull();
  });
});

describe("formatElapsedTime — 걸린 시간 (07 작업 목록·결과 요약)", () => {
  it.each([
    [3780, "1시간 3분"],
    [720, "12분"],
    [45, "1분 이내"],
  ])("%s초 → %s", (input, expected) => {
    expect(formatElapsedTime(input)).toBe(expected);
  });
});

describe("formatCrawlJobStatus — 수집 작업 상태 문구", () => {
  it.each([
    ["RUNNING", "진행 중"],
    ["PAUSED", "일시 정지"],
    ["COMPLETED", "완료"],
    ["STOPPED", "중지됨"],
    ["FAILED", "실패"],
  ])("%s → %s", (input, expected) => {
    expect(formatCrawlJobStatus(input)).toBe(expected);
  });
});
