import {
  DEFAULT_SCOPE,
  SEARCH_SCOPES,
  SCOPE_EXAMPLES,
  SCOPE_FIELD_LABEL,
  buildSearchHref,
  normalizeScope,
  readScopeParam,
  scopePlaceholder,
  withScope,
} from "./searchScope.js";

// frontend-dev 내부 단위 — 02 §3-1(in 관용 규칙) · 08 §4-1(주소) · §4-3(자리 문구) · §4-5(예시 칩)

describe("normalizeScope / readScopeParam", () => {
  it("계약값 세 개만 인정하고 나머지는 전체", () => {
    expect(SEARCH_SCOPES.map((s) => s.value)).toEqual(["ALL", "TITLE", "COMPOSER"]);
    expect(normalizeScope("TITLE")).toBe("TITLE");
    expect(normalizeScope("title")).toBe(DEFAULT_SCOPE);
    expect(normalizeScope("xyz")).toBe(DEFAULT_SCOPE);
    expect(normalizeScope(undefined)).toBe(DEFAULT_SCOPE);
  });

  it("주소 쿼리는 대소문자를 무시한다 (서버와 같은 규칙)", () => {
    expect(readScopeParam("title")).toBe("TITLE");
    expect(readScopeParam("Composer")).toBe("COMPOSER");
    expect(readScopeParam("TITLE_KO")).toBe("ALL");
    expect(readScopeParam(null)).toBe("ALL");
    expect(readScopeParam("")).toBe("ALL");
  });
});

describe("withScope — 기본값은 주소에 쓰지 않는다 (02 §0-5)", () => {
  it("전체면 in 을 떼고, 아니면 대문자로 싣는다", () => {
    const base = new URLSearchParams("q=녹턴&in=TITLE&level=INTERMEDIATE");
    expect(withScope(base, "ALL").has("in")).toBe(false);
    expect(withScope(base, "COMPOSER").get("in")).toBe("COMPOSER");
    expect(withScope(base, "COMPOSER").get("level")).toBe("INTERMEDIATE");
    expect(base.get("in")).toBe("TITLE"); // 원본은 건드리지 않는다
  });
});

describe("buildSearchHref — 검색창·예시 칩이 만드는 주소", () => {
  it("검색어만 있으면 q 하나, 기준·출처가 있으면 in·from 이 붙는다", () => {
    expect(buildSearchHref("/piano/search", { q: "월광" })).toBe("/piano/search?q=%EC%9B%94%EA%B4%91");
    expect(buildSearchHref("/piano/search", { q: "a b", scope: "TITLE" })).toBe("/piano/search?q=a%20b&in=TITLE");
    expect(buildSearchHref("/piano/search", { q: "a", scope: "ALL", from: "violin" })).toBe(
      "/piano/search?q=a&from=violin",
    );
    expect(buildSearchHref("/piano/search", { q: "a", scope: "xyz" })).toBe("/piano/search?q=a");
  });
});

describe("scopePlaceholder — 08 §4-3 자리 문구 3종 + 짧은 버전", () => {
  it.each([
    ["ALL", "곡 이름, 작곡가, 작품번호로 찾기 — 예: 월광, 쇼팽 녹턴, K.545", "곡 이름, 작곡가, 작품번호 — 예: 월광, K.545"],
    ["TITLE", "곡 이름으로 찾기 — 예: 월광, 엘리제를 위하여, Op.27 No.2", "곡 이름 — 예: 월광, Op.27 No.2"],
    ["COMPOSER", "작곡가 이름으로 찾기 — 예: 쇼팽, 베토벤, Chopin", "작곡가 이름 — 예: 쇼팽, 베토벤"],
  ])("%s", (scope, long, short) => {
    expect(scopePlaceholder(scope, false)).toBe(long);
    expect(scopePlaceholder(scope, true)).toBe(short);
  });

  it("알 수 없는 기준은 전체 문구", () => {
    expect(scopePlaceholder("xyz", false)).toBe(scopePlaceholder("ALL", false));
  });
});

describe("예시 칩·칸 이름", () => {
  it("08 §4-5 칩 3종", () => {
    expect(SCOPE_EXAMPLES.ALL).toEqual(["월광", "쇼팽 녹턴", "K.545"]);
    expect(SCOPE_EXAMPLES.TITLE).toEqual(["월광", "엘리제를 위하여", "Op.27 No.2"]);
    expect(SCOPE_EXAMPLES.COMPOSER).toEqual(["쇼팽", "베토벤", "모차르트"]);
  });

  it("기준이 찾는 칸 이름 — 건수 접미사·0건 문구에 쓴다", () => {
    expect(SCOPE_FIELD_LABEL.TITLE).toBe("곡명");
    expect(SCOPE_FIELD_LABEL.COMPOSER).toBe("작곡가 이름");
    expect(SCOPE_FIELD_LABEL.ALL).toBeUndefined();
  });
});
