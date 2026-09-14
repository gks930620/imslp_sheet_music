/**
 * 악기 구분 (02_API §0-7 · 03_기술결정 §21).
 *
 * 화면 주소는 소문자 슬러그(`/piano`), API·응답은 대문자 enum(`PIANO`).
 * 슬러그 ↔ enum 변환은 이 파일 한 곳에서만 한다. 구분은 3개로 고정이다(기획 04 §9 8-1).
 *
 * `open` — 열린 구분인가. 준비 중 구분(`false`)은 안내 화면 하나뿐이고 그 아래 하위 경로가 없다.
 * `imslpUrl` — 준비 중 안내 화면의 IMSLP 출구(검색어 없이 들어왔을 때 쓰는 그 구분의 일반 주소, 08 §2-3).
 */
export const SECTIONS = [
  { slug: "piano", code: "PIANO", label: "피아노", open: true, imslpUrl: "https://imslp.org/wiki/Category:For_piano" },
  { slug: "violin", code: "VIOLIN", label: "바이올린", open: false, imslpUrl: "https://imslp.org/wiki/Category:For_violin" },
  {
    slug: "orchestra",
    code: "ORCHESTRA",
    label: "오케스트라",
    open: false,
    imslpUrl: "https://imslp.org/wiki/Category:For_orchestra",
  },
];

/** 기본 구분 — 구분이 없던 시절의 주소·옛 링크·구분 밖 화면의 링크가 향하는 곳 (02 §0-5) */
export const DEFAULT_SECTION = SECTIONS[0];

/** 슬러그는 주소 어휘라 소문자 그대로만 인정한다 */
export function findSectionBySlug(slug) {
  if (typeof slug !== "string") return null;
  return SECTIONS.find((section) => section.slug === slug) ?? null;
}

/** enum 은 대소문자를 무시한다 (02 §0-7 — 서버도 같은 규칙) */
export function findSectionByCode(code) {
  if (typeof code !== "string") return null;
  const upper = code.toUpperCase();
  return SECTIONS.find((section) => section.code === upper) ?? null;
}

/** 경로 첫 세그먼트에서 구분을 읽는다. 구분 슬러그가 아니면(`/admin`, `/cello`, `/`) null */
export function sectionFromPathname(pathname) {
  const [, first = ""] = (pathname ?? "").split("/");
  return findSectionBySlug(first);
}

/**
 * 구분 안 화면으로 가는 링크가 쓰는 구분 (02 §0-5 링크 규칙).
 * 현재 구분이 없거나(404·관리) 준비 중이면 기본 구분 — 준비 중 구분 아래에는 화면이 없다.
 */
export function linkSection(section) {
  return section?.open ? section : DEFAULT_SECTION;
}

/** `/{구분}{subpath}` — 링크 규칙을 적용한 주소 */
export function sectionPath(section, subpath = "") {
  return `/${linkSection(section).slug}${subpath}`;
}
