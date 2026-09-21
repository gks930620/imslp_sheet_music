/**
 * 최근 본 곡 — 이 브라우저의 선반 (03_기술결정 §23, 기획 05 §4-2).
 *
 * 담는 것은 **곡 id 뿐**이다. 제목·뱃지를 담으면 화면이 "저장한 순간의 정보" 를 그려서
 * 고쳐진 제목·열린 곡이 반영되지 않고, 숨긴 곡을 눌러 404 를 만난다(인수 조건 8-E 7·8).
 * 표시는 매번 `GET /api/works/recent`(02 §3-9)로 지금 값을 받는다.
 *
 * 저장소는 `localStorage` 다 — 브라우저를 닫아도, **로그아웃해도** 남아야 한다(8-E 5).
 * 저장이 막힌 환경(시크릿·용량 초과)에서는 **조용히** 없는 것이 된다. 오류 문구를 만들지 않는다.
 */

export const RECENT_WORKS_KEY = "sheetmusic.recentWorks";

/** 상한 10. 넘치면 뒤에서 버린다 (8-E 4) */
export const RECENT_WORKS_LIMIT = 10;

/** `{"PIANO":[23,21],"VIOLIN":[…]}` — 구분별 배열, 최신이 앞 */
function readAll() {
  try {
    const parsed = JSON.parse(localStorage.getItem(RECENT_WORKS_KEY) ?? "{}");
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
    return parsed;
  } catch {
    return {}; // 값이 깨졌으면 빈 것으로 본다
  }
}

function writeAll(map) {
  try {
    localStorage.setItem(RECENT_WORKS_KEY, JSON.stringify(map));
  } catch {
    /* 저장 차단·용량 초과 — 조용히 넘어간다 */
  }
}

function toId(value) {
  return Number.isInteger(value) && value > 0 ? value : null;
}

/** 그 구분에서 최근에 본 곡 id (최신 순, 최대 10). 없거나 깨졌으면 빈 배열 */
export function readRecentWorkIds(sectionCode) {
  const list = readAll()[sectionCode];
  if (!Array.isArray(list)) return [];
  return list.map(toId).filter(Boolean).slice(0, RECENT_WORKS_LIMIT);
}

/** 곡 상세가 정상으로 그려진 뒤에만 부른다 — 이미 있으면 맨 앞으로 올리고 중복을 만들지 않는다 (8-E 3) */
export function rememberRecentWork(sectionCode, workId) {
  const id = toId(Number(workId));
  if (!sectionCode || !id) return;
  const next = [id, ...readRecentWorkIds(sectionCode).filter((value) => value !== id)].slice(0, RECENT_WORKS_LIMIT);
  writeAll({ ...readAll(), [sectionCode]: next });
}

/** 지금 구분만 비운다 — 다른 구분의 목록은 그대로다 (8-E 6) */
export function clearRecentWorks(sectionCode) {
  if (!sectionCode) return;
  writeAll({ ...readAll(), [sectionCode]: [] });
}
