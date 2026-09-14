/**
 * 검색 기준 `in` (02_API §3-1 · 화면정의 08 §4).
 *
 * 값은 계약과 같은 대문자 `ALL | TITLE | COMPOSER`. "전체"는 기본값이라 주소에 쓰지 않는다(02 §0-5).
 * 주소·응답의 알 수 없는 값은 오류가 아니라 전체다(기획 04 §4-5, 인수 조건 8-F 4).
 */

export const SEARCH_SCOPES = [
  { value: "ALL", label: "전체" },
  { value: "TITLE", label: "곡명" },
  { value: "COMPOSER", label: "작곡가" },
];

export const DEFAULT_SCOPE = "ALL";

/** 기준이 찾는 칸의 이름 — 건수 접미사(`· 곡명에서 찾음`)·0건 문구에 쓴다. 전체는 이름이 없다 */
export const SCOPE_FIELD_LABEL = {
  TITLE: "곡명",
  COMPOSER: "작곡가 이름",
};

/** 08 §4-3 — 기준별 자리 문구. short 는 360px 급에서 잘릴 때의 짧은 버전 */
const PLACEHOLDERS = {
  ALL: {
    long: "곡 이름, 작곡가, 작품번호로 찾기 — 예: 월광, 쇼팽 녹턴, K.545",
    short: "곡 이름, 작곡가, 작품번호 — 예: 월광, K.545",
  },
  TITLE: {
    long: "곡 이름으로 찾기 — 예: 월광, 엘리제를 위하여, Op.27 No.2",
    short: "곡 이름 — 예: 월광, Op.27 No.2",
  },
  COMPOSER: {
    long: "작곡가 이름으로 찾기 — 예: 쇼팽, 베토벤, Chopin",
    short: "작곡가 이름 — 예: 쇼팽, 베토벤",
  },
};

/** 08 §4-5 — 홈 예시 칩은 기준을 따라 바뀐다 (곡명 기준에 "쇼팽 녹턴" 칩을 주면 0건 함정) */
export const SCOPE_EXAMPLES = {
  ALL: ["월광", "쇼팽 녹턴", "K.545"],
  TITLE: ["월광", "엘리제를 위하여", "Op.27 No.2"],
  COMPOSER: ["쇼팽", "베토벤", "모차르트"],
};

/** 계약값 세 개만 인정한다. 그 밖은 전체 */
export function normalizeScope(value) {
  return SEARCH_SCOPES.some((scope) => scope.value === value) ? value : DEFAULT_SCOPE;
}

/** 주소 쿼리 `in` 을 읽는다 — 서버와 같은 규칙(대소문자 무시, 셋 밖이면 전체) */
export function readScopeParam(raw) {
  return normalizeScope(typeof raw === "string" ? raw.toUpperCase() : raw);
}

/** 기준을 바꾼 새 쿼리 — 전체면 `in` 을 뗀다. 원본은 건드리지 않는다 */
export function withScope(searchParams, scope) {
  const next = new URLSearchParams(searchParams);
  const normalized = normalizeScope(scope);
  if (normalized === DEFAULT_SCOPE) next.delete("in");
  else next.set("in", normalized);
  return next;
}

/**
 * 검색창·예시 칩이 만드는 검색 결과 주소.
 * `from` 은 준비 중 화면의 헤더 검색이 붙이는 화면 전용 쿼리(02 §0-5) — 결과 화면이 API 로 넘기지 않는다.
 */
export function buildSearchHref(searchPath, { q, scope = DEFAULT_SCOPE, from } = {}) {
  let href = `${searchPath}?q=${encodeURIComponent(q)}`;
  const normalized = normalizeScope(scope);
  if (normalized !== DEFAULT_SCOPE) href += `&in=${normalized}`;
  if (from) href += `&from=${encodeURIComponent(from)}`;
  return href;
}

export function scopePlaceholder(scope, short = false) {
  const entry = PLACEHOLDERS[normalizeScope(scope)];
  return short ? entry.short : entry.long;
}
