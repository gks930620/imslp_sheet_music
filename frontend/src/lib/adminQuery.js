/**
 * 관리 목록 화면(작곡가·곡·저작권 대기함)의 URL 쿼리 규칙.
 * lib/workQuery.js 와 같은 관례: URL 쿼리가 상태의 원본, 필터가 바뀌면 page 를 지운다.
 * API 로 보낼 쿼리 문자열은 workQuery.js 의 buildWorkQuery(searchParams, keys) 를 그대로 쓴다(빈 값 제외).
 */

/** 필터 값을 바꾼 새 URLSearchParams. 빈 값·false 는 쿼리에서 뺀다. page 는 항상 지운다 */
export function applyAdminFilter(searchParams, changes) {
  const next = new URLSearchParams(searchParams);
  Object.entries(changes).forEach(([key, value]) => {
    if (value === null || value === undefined || value === "" || value === false) next.delete(key);
    else next.set(key, String(value));
  });
  next.delete("page");
  return next;
}

/** 주어진 키 중 하나라도 값이 있으면 true (빈 목록 문구를 고르는 기준) */
export function hasAdminFilter(searchParams, keys) {
  return keys.some((key) => Boolean(searchParams.get(key)));
}
