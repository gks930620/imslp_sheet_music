import {
  DEFAULT_SECTION,
  SECTIONS,
  findSectionByCode,
  findSectionBySlug,
  linkSection,
  sectionFromPathname,
  sectionPath,
} from "./sections.js";

// frontend-dev 내부 단위 — 02 §0-7(슬러그 ↔ enum 변환은 이 파일 한 곳) · 03 §21-1(주소 구조) · §0-5(링크 규칙)

describe("SECTIONS — 고정 3개, 피아노만 열림", () => {
  it("슬러그·enum·라벨이 계약과 같다", () => {
    expect(SECTIONS.map((s) => [s.slug, s.code, s.label, s.open])).toEqual([
      ["piano", "PIANO", "피아노", true],
      ["violin", "VIOLIN", "바이올린", false],
      ["orchestra", "ORCHESTRA", "오케스트라", false],
    ]);
    expect(DEFAULT_SECTION.slug).toBe("piano");
  });
});

describe("findSectionBySlug / findSectionByCode", () => {
  it("슬러그는 소문자 그대로만 인정한다 (주소 어휘)", () => {
    expect(findSectionBySlug("violin")?.code).toBe("VIOLIN");
    expect(findSectionBySlug("Violin")).toBeNull();
    expect(findSectionBySlug("cello")).toBeNull();
    expect(findSectionBySlug(undefined)).toBeNull();
  });

  it("enum 은 대소문자를 무시한다 (02 §0-7)", () => {
    expect(findSectionByCode("PIANO")?.slug).toBe("piano");
    expect(findSectionByCode("piano")?.slug).toBe("piano");
    expect(findSectionByCode("CELLO")).toBeNull();
    expect(findSectionByCode(null)).toBeNull();
  });
});

describe("sectionFromPathname — 경로 첫 세그먼트", () => {
  it.each([
    ["/piano", "piano"],
    ["/piano/search", "piano"],
    ["/violin", "violin"],
    ["/orchestra/works/3", "orchestra"],
  ])("%s → %s", (pathname, slug) => {
    expect(sectionFromPathname(pathname)?.slug).toBe(slug);
  });

  it.each(["/", "", "/admin/works", "/cello", "/login", "/첼로<script>", undefined])("%s → null", (pathname) => {
    expect(sectionFromPathname(pathname)).toBeNull();
  });
});

describe("linkSection / sectionPath — 구분 안 링크 규칙 (02 §0-5)", () => {
  it("열린 구분이면 그 구분, 없거나 준비 중이면 기본 구분", () => {
    expect(linkSection(findSectionBySlug("piano")).slug).toBe("piano");
    expect(linkSection(findSectionBySlug("violin")).slug).toBe("piano");
    expect(linkSection(null).slug).toBe("piano");
  });

  it("sectionPath 는 접두사를 붙인다", () => {
    expect(sectionPath(null)).toBe("/piano");
    expect(sectionPath(findSectionBySlug("piano"), "/works/21")).toBe("/piano/works/21");
    expect(sectionPath(findSectionBySlug("violin"), "/search")).toBe("/piano/search");
  });
});
