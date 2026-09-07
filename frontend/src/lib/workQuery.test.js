import { readWorkFilters, applyWorkFilters } from "./workQuery.js";

// frontend-dev 자체 테스트: 검색 결과·작곡가 상세가 같은 쿼리 이름을 쓰므로(00 §8) 한 곳에 모은다.
function params(search) {
  return new URLSearchParams(search);
}

describe("readWorkFilters", () => {
  it("쿼리에서 난이도(복수)·쪽수·바로 받기를 읽는다", () => {
    const filters = readWorkFilters(params("q=녹턴&level=ELEMENTARY,INTERMEDIATE&pages=LE10&downloadable=true"));
    expect(filters).toEqual({
      levels: ["ELEMENTARY", "INTERMEDIATE"],
      pages: "LE10",
      downloadable: true,
      hasFilter: true,
    });
  });

  it("필터가 없으면 hasFilter false", () => {
    expect(readWorkFilters(params("q=녹턴"))).toEqual({ levels: [], pages: null, downloadable: false, hasFilter: false });
  });
});

describe("applyWorkFilters", () => {
  it("필터를 쓰고 page 는 지운다 (q 등 나머지는 유지)", () => {
    const next = applyWorkFilters(params("q=녹턴&page=3&sort=opus"), {
      levels: ["ELEMENTARY"],
      pages: "GE21",
      downloadable: true,
    });
    expect(next.get("q")).toBe("녹턴");
    expect(next.get("sort")).toBe("opus");
    expect(next.get("level")).toBe("ELEMENTARY");
    expect(next.get("pages")).toBe("GE21");
    expect(next.get("downloadable")).toBe("true");
    expect(next.has("page")).toBe(false);
  });

  it("빈 필터는 쿼리에서 제거한다", () => {
    const next = applyWorkFilters(params("q=녹턴&level=ADVANCED&pages=LE10&downloadable=true"), {
      levels: [],
      pages: null,
      downloadable: false,
    });
    expect(next.has("level")).toBe(false);
    expect(next.has("pages")).toBe(false);
    expect(next.has("downloadable")).toBe(false);
    expect(next.get("q")).toBe("녹턴");
  });
});
