/**
 * 곡 목록 화면(검색 결과·작곡가 상세)의 URL 쿼리 ↔ 필터 상태 변환.
 * 쿼리 이름은 API 와 동일하다 (00_공통 §8, 02_API §3-1·§3-8).
 */

const FILTER_KEYS = ["level", "pages", "downloadable"];
/** API 로 그대로 넘기는 쿼리 이름 */
export const FORWARDED_KEYS = ["q", "sort", "level", "pages", "downloadable", "page"];

export function readWorkFilters(searchParams) {
  const levels = (searchParams.get("level") ?? "").split(",").filter(Boolean);
  const pages = searchParams.get("pages");
  const downloadable = searchParams.get("downloadable") === "true";
  return {
    levels,
    pages: pages || null,
    downloadable,
    hasFilter: levels.length > 0 || Boolean(pages) || downloadable,
  };
}

export function applyWorkFilters(searchParams, { levels = [], pages = null, downloadable = false }) {
  const next = new URLSearchParams(searchParams);
  FILTER_KEYS.forEach((key) => next.delete(key));
  if (levels.length) next.set("level", levels.join(","));
  if (pages) next.set("pages", pages);
  if (downloadable) next.set("downloadable", "true");
  next.delete("page");
  return next;
}

/** URL 쿼리 중 API 로 넘길 것만 추려 쿼리 문자열을 만든다 (size 는 보내지 않는다) */
export function buildWorkQuery(searchParams, keys = FORWARDED_KEYS) {
  const next = new URLSearchParams();
  keys.forEach((key) => {
    const value = searchParams.get(key);
    if (value) next.set(key, value);
  });
  return next.toString();
}

/** page 쿼리만 바꾼다 (0 이면 제거) */
export function withPage(searchParams, page) {
  const next = new URLSearchParams(searchParams);
  if (page > 0) next.set("page", String(page));
  else next.delete("page");
  return next;
}
