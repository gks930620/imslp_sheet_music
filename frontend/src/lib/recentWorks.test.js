import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  RECENT_WORKS_KEY,
  RECENT_WORKS_LIMIT,
  clearRecentWorks,
  readRecentWorkIds,
  rememberRecentWork,
} from "./recentWorks.js";

/**
 * 최근 본 곡의 브라우저 저장 — 03_기술결정 §23, 기획 05 §4-2, 인수 조건 8-E 3·4·5·6·10.
 *
 * <b>저장하는 것은 곡 id 뿐이다</b>(제목·뱃지를 저장하면 화면이 "저장한 순간의 정보" 를 그린다 — 8-E 7·8).
 * 저장소는 localStorage 다: 브라우저를 닫아도 남고 <b>로그아웃해도 남아야</b> 하기 때문이다(8-E 5).
 */
describe("recentWorks — 이 브라우저의 선반", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it("아무것도 안 본 브라우저는 빈 배열 — 시크릿 창의 홈이 지금과 똑같은 근거 (8-E 2·10)", () => {
    expect(readRecentWorkIds("PIANO")).toEqual([]);
  });

  it("맨 앞에 쌓인다 — 마지막에 본 곡이 앞 (8-E 1)", () => {
    rememberRecentWork("PIANO", 21);
    rememberRecentWork("PIANO", 22);
    rememberRecentWork("PIANO", 23);

    expect(readRecentWorkIds("PIANO")).toEqual([23, 22, 21]);
  });

  it("같은 곡을 다시 열면 맨 앞으로 올라올 뿐 두 번 나오지 않는다 (8-E 3)", () => {
    rememberRecentWork("PIANO", 21);
    rememberRecentWork("PIANO", 22);
    rememberRecentWork("PIANO", 21);

    expect(readRecentWorkIds("PIANO")).toEqual([21, 22]);
  });

  it("10곡이 상한 — 11곡째를 열면 가장 오래된 곡이 빠진다 (8-E 4)", () => {
    expect(RECENT_WORKS_LIMIT).toBe(10);
    for (let id = 1; id <= 11; id += 1) {
      rememberRecentWork("PIANO", id);
    }

    const ids = readRecentWorkIds("PIANO");
    expect(ids).toHaveLength(10);
    expect(ids[0]).toBe(11);
    expect(ids).not.toContain(1);
  });

  it("구분마다 따로다 — 피아노 홈에는 피아노 곡만 (기획 05 §5-6)", () => {
    rememberRecentWork("PIANO", 21);
    rememberRecentWork("VIOLIN", 91);

    expect(readRecentWorkIds("PIANO")).toEqual([21]);
    expect(readRecentWorkIds("VIOLIN")).toEqual([91]);
  });

  it("지우기는 지금 구분만 비운다 (8-E 6)", () => {
    rememberRecentWork("PIANO", 21);
    rememberRecentWork("VIOLIN", 91);

    clearRecentWorks("PIANO");

    expect(readRecentWorkIds("PIANO")).toEqual([]);
    expect(readRecentWorkIds("VIOLIN")).toEqual([91]);
  });

  it("지운 뒤 한 곡을 열면 그 1곡으로 다시 생긴다 (8-E 6)", () => {
    rememberRecentWork("PIANO", 21);
    clearRecentWorks("PIANO");
    rememberRecentWork("PIANO", 22);

    expect(readRecentWorkIds("PIANO")).toEqual([22]);
  });

  it("localStorage 에 담는다 — 로그아웃·브라우저 종료를 넘어 남아야 한다 (8-E 5)", () => {
    rememberRecentWork("PIANO", 21);

    expect(localStorage.getItem(RECENT_WORKS_KEY)).not.toBeNull();
    expect(sessionStorage.getItem(RECENT_WORKS_KEY)).toBeNull();
  });

  it("값이 깨져 있으면 빈 배열로 본다 — 오류를 던지지 않는다", () => {
    localStorage.setItem(RECENT_WORKS_KEY, "{망가진 값");
    expect(readRecentWorkIds("PIANO")).toEqual([]);

    localStorage.setItem(RECENT_WORKS_KEY, JSON.stringify({ PIANO: ["스물하나", null, 21] }));
    expect(readRecentWorkIds("PIANO")).toEqual([21]);
  });

  it("저장이 막힌 환경에서는 조용히 넘어간다 — 오류 문구를 만들지 않는다 (기획 05 §4-2)", () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("QuotaExceededError");
    });

    expect(() => rememberRecentWork("PIANO", 21)).not.toThrow();
    expect(readRecentWorkIds("PIANO")).toEqual([]);

    setItem.mockRestore();
  });
});
