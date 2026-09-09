/**
 * IMSLP 로 내보내는 주소 만들기.
 *
 * 우리는 IMSLP 위에 얹는 레이어라, 우리가 못 주는 것은 원본으로 정직하게 안내한다(기획 §10-7).
 */

const IMSLP_SEARCH_BASE = "https://imslp.org/index.php?title=Special:Search&search=";

/** 검색어를 실은 IMSLP 검색 주소. 검색어가 없으면 null (보낼 곳 없는 링크는 만들지 않는다) */
export function imslpSearchUrl(q) {
  const keyword = (q ?? "").trim();
  if (!keyword) return null;
  return `${IMSLP_SEARCH_BASE}${encodeURIComponent(keyword)}`;
}
