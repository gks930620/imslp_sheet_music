import { buildWorkQuery, FORWARDED_KEYS } from "./workQuery.js";

// 02 §3-1(쿼리 이름) · §0-5(화면 전용 쿼리는 API 로 넘기지 않는다)
// 이 파일이 잠그는 것은 한 줄이다: **무엇을 서버에 넘기고 무엇을 안 넘기는가.**

describe("검색 결과 화면이 API 로 넘기는 쿼리", () => {
  it("검색 기준 in 은 넘긴다", () => {
    expect(FORWARDED_KEYS).toContain("in");
  });

  it("화면 전용 쿼리 from 은 넘기지 않는다 — 서버에 그런 파라미터가 없다", () => {
    expect(FORWARDED_KEYS).not.toContain("from");
  });

  it("buildWorkQuery 는 in 을 싣고 from 을 뺀다", () => {
    const params = new URLSearchParams("q=녹턴&in=TITLE&level=INTERMEDIATE&page=1&from=violin");
    const query = new URLSearchParams(buildWorkQuery(params));
    expect(query.get("q")).toBe("녹턴");
    expect(query.get("in")).toBe("TITLE");
    expect(query.get("level")).toBe("INTERMEDIATE");
    expect(query.get("page")).toBe("1");
    expect(query.has("from")).toBe(false);
    expect(query.has("size")).toBe(false);
  });

  it("in 이 없으면 만들어 넣지 않는다 — 기본값은 서버가 안다 (§3-1 관용 규칙)", () => {
    const query = new URLSearchParams(buildWorkQuery(new URLSearchParams("q=녹턴")));
    expect(query.has("in")).toBe(false);
  });
});
