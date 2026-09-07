import { describe, expect, it } from "vitest";
import { applyAdminFilter, hasAdminFilter } from "./adminQuery.js";

// 내부 구현 세부(관리 목록 3개 공통 규칙)라 frontend-dev 가 직접 먼저 작성한 테스트.
// lib/workQuery.js 관례와 같게: 필터가 바뀌면 page 를 지우고, 빈 값은 쿼리에서 뺀다.
function params(search) {
  return new URLSearchParams(search);
}

describe("applyAdminFilter", () => {
  it("값을 넣고 page 를 지운다", () => {
    const next = applyAdminFilter(params("q=녹턴&page=2"), { status: "NEEDS_WORK" });
    expect(next.get("q")).toBe("녹턴");
    expect(next.get("status")).toBe("NEEDS_WORK");
    expect(next.has("page")).toBe(false);
  });

  it("빈 값·false 는 쿼리에서 뺀다", () => {
    const next = applyAdminFilter(params("q=녹턴&missingKo=true&status=HIDDEN"), {
      q: "",
      missingKo: false,
      status: null,
    });
    expect(next.toString()).toBe("");
  });

  it("다른 쿼리는 건드리지 않는다", () => {
    const next = applyAdminFilter(params("composerId=9&level=NONE"), { q: "녹턴" });
    expect(next.get("composerId")).toBe("9");
    expect(next.get("level")).toBe("NONE");
  });
});

describe("hasAdminFilter", () => {
  it("주어진 키 중 하나라도 값이 있으면 true", () => {
    expect(hasAdminFilter(params("page=2"), ["q", "status"])).toBe(false);
    expect(hasAdminFilter(params("status=HIDDEN&page=2"), ["q", "status"])).toBe(true);
    expect(hasAdminFilter(params("q="), ["q"])).toBe(false);
  });
});
