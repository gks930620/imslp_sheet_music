import { describe, expect, it } from "vitest";
import { formatReceivedDate } from "./format.js";

/**
 * 받은 악보의 날짜 한 줄 — 00_공통 §4 날짜 규칙(2026-09-20), 화면정의 09 §1-2-1.
 * `오늘 받음` / `어제 받음` / `9월 12일 받음` / `2025년 9월 12일 받음`. **시·분은 쓰지 않는다**
 * (선반이지 영수증이 아니다 — 기획 05 §3-2). 기존 `formatRelativeTime` 의 "N시간 전" 은 쓰지 않는다.
 *
 * 컨벤션 §6 — 시간에 기대는 로직은 <b>지금</b>을 인자로 받아 결정적으로 시험한다.
 * 입력은 서버가 주는 ISO UTC 문자열이고, 갈리는 기준은 <b>사용자 지역 시각의 달력 날짜</b>다.
 * 그래서 아래 픽스처는 실행 시간대에 기대지 않도록 지역 시각으로 만들어 ISO 로 바꿔 넣는다.
 */
describe("formatReceivedDate(value, now)", () => {
  const now = new Date(2026, 8, 20, 13, 0); // 2026-09-20 13:00 (지역 시각)
  const iso = (year, month, day, hour = 12) => new Date(year, month, day, hour).toISOString();

  it("오늘 받았으면 '오늘 받음'", () => {
    expect(formatReceivedDate(iso(2026, 8, 20, 1), now)).toBe("오늘 받음");
  });

  it("어제 받았으면 '어제 받음'", () => {
    expect(formatReceivedDate(iso(2026, 8, 19, 23), now)).toBe("어제 받음");
  });

  it("같은 해면 '9월 12일 받음' — 시·분은 없다", () => {
    expect(formatReceivedDate(iso(2026, 8, 12, 17), now)).toBe("9월 12일 받음");
  });

  it("다른 해면 '2025년 9월 12일 받음'", () => {
    expect(formatReceivedDate(iso(2025, 8, 12, 17), now)).toBe("2025년 9월 12일 받음");
  });

  it("같은 날이면 시각이 몇 시든 '오늘 받음' — 달력 날짜로 가른다", () => {
    expect(formatReceivedDate(iso(2026, 8, 20, 0), now)).toBe("오늘 받음");
    expect(formatReceivedDate(iso(2026, 8, 20, 12), now)).toBe("오늘 받음");
  });

  it("값이 없거나 이상하면 빈 문자열 — 화면은 줄을 만들지 않는다", () => {
    expect(formatReceivedDate(null, now)).toBe("");
    expect(formatReceivedDate("어제", now)).toBe("");
  });
});
