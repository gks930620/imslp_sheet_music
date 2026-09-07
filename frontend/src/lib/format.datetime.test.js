import { formatAdminDateTime, formatShortDateTime, formatClockTime, formatCount } from "./format.js";

// frontend-dev 자체 테스트: 관리 화면 날짜 표기(00_공통 §4 "2026-09-05 14:32", 07 "09-06 14:02" / "14:23:10").
// 지역 시각으로 찍히므로 값이 아니라 형식을 고정한다.
describe("관리 화면 날짜·시각 표기", () => {
  it("formatAdminDateTime — YYYY-MM-DD HH:mm", () => {
    expect(formatAdminDateTime("2026-09-06T05:02:00Z")).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
  });

  it("formatShortDateTime — MM-DD HH:mm", () => {
    expect(formatShortDateTime("2026-09-06T05:02:00Z")).toMatch(/^\d{2}-\d{2} \d{2}:\d{2}$/);
  });

  it("formatClockTime — HH:mm (초 포함 옵션)", () => {
    expect(formatClockTime("2026-09-06T05:35:00Z")).toMatch(/^\d{2}:\d{2}$/);
    expect(formatClockTime("2026-09-06T05:35:10Z", { seconds: true })).toMatch(/^\d{2}:\d{2}:\d{2}$/);
  });

  it("값이 없거나 잘못됐으면 '-'", () => {
    expect(formatAdminDateTime(null)).toBe("-");
    expect(formatShortDateTime("이상한 값")).toBe("-");
    expect(formatClockTime(undefined)).toBe("-");
  });
});

describe("formatCount — 천 단위 쉼표", () => {
  it.each([
    [0, "0"],
    [312, "312"],
    [1204, "1,204"],
    [null, "0"],
  ])("%s → %s", (input, expected) => {
    expect(formatCount(input)).toBe(expected);
  });
});
