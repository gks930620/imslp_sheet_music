/**
 * 내 악보(즐겨찾기 · 받은 악보) 픽스처 — 02_API_명세서 §10-2 · §10-3 · §2-4 의 JSON 예시 그대로.
 *
 * 기존 `fixtures.js` 에 **커밋되지 않은 이전 라운드 변경**이 있어 그 파일에 손대지 않고 여기 둔다
 * (02 §9-3 의 `fixtures.js` 항목과 같은 이유). 곡 카드는 그 파일 것을 그대로 쓴다 —
 * 내 악보의 항목은 다른 목록과 **같은 곡 카드**이기 때문이다(기획 05 §2-2).
 */

import { pageResponse, workSummary, composerChopin } from "./fixtures.js";

/** §2-4 EditionBriefDTO — "받은 판본: 전체 악보 · 전곡 · 5쪽 · 1.1MB" 한 줄의 재료 */
export function editionBrief(overrides = {}) {
  return {
    id: 301,
    kind: "COMPLETE_SCORE",
    scope: "COMPLETE",
    movementNumber: null,
    pageCount: 5,
    fileSize: 1153434, // 1.1MB
    ...overrides,
  };
}

/** 받은 악보 항목의 곡 (화면정의 09 의 예시 — 쇼팽 녹턴 2번, 바로 받기 가능) */
export const libraryWorkNocturne = workSummary({
  id: 23,
  titleKo: "녹턴 2번",
  titleOriginal: "Nocturne in E-flat major, Op.9 No.2",
  composer: composerChopin,
  catalogNumbers: ["Op.9 No.2"],
  level: "INTERMEDIATE",
  status: "READY",
  pageCount: 5,
  fileSize: 1153434,
  previewUrl: null,
  matchedAlias: null,
  scopeNote: null,
});

/** §10-3 ReceivedWorkDTO — 기본은 상태 ①(그대로 받을 수 있음) */
export function receivedRow(overrides = {}) {
  return {
    work: libraryWorkNocturne,
    downloadedAt: "2026-09-12T08:12:00Z",
    receivedEdition: editionBrief(),
    redownloadState: "AVAILABLE",
    redownloadUrl: "/api/editions/301/download",
    alternativeEdition: null,
    ...overrides,
  };
}

/** ② 받을 수는 있는데 지금 추천이 다름 */
export function receivedRowRecommendationChanged(overrides = {}) {
  return receivedRow({ redownloadState: "RECOMMENDATION_CHANGED", ...overrides });
}

/** ③-a 그때 판본은 못 주고, 지금 추천 판본은 바로 받을 수 있음 */
export function receivedRowWithAlternative(overrides = {}) {
  return receivedRow({
    receivedEdition: editionBrief({ id: null, pageCount: 5, fileSize: 1153434 }),
    redownloadState: "UNAVAILABLE",
    redownloadUrl: "/api/editions/302/download",
    alternativeEdition: editionBrief({ id: 302, pageCount: 12, fileSize: 2516582 }),
    ...overrides,
  });
}

/** ③-b 그때 판본도 지금 추천도 못 줌 — 버튼 없음, 곡 보기만 */
export function receivedRowUnavailable(overrides = {}) {
  return receivedRow({
    redownloadState: "UNAVAILABLE",
    redownloadUrl: null,
    alternativeEdition: null,
    ...overrides,
  });
}

/** 두 탭의 숫자 (§10-2 · §10-3 — 어느 탭에 있든 함께 온다) */
export function libraryCounts(favorites = 2, downloads = 1) {
  return { favorites, downloads };
}

/** §10-2 응답 data */
export function favoritesResponse({
  works = [workSummary(), workSummary({ id: 22, titleKo: "엘리제를 위하여", titleOriginal: "Für Elise, WoO 59" })],
  counts,
  page = 0,
  size = 20,
  totalElements = works.length,
} = {}) {
  return {
    counts: counts ?? libraryCounts(totalElements, 0),
    works: pageResponse(works, { page, size, totalElements }),
  };
}

/** §10-3 응답 data */
export function downloadsResponse({
  items = [receivedRow()],
  counts,
  page = 0,
  size = 20,
  totalElements = items.length,
} = {}) {
  return {
    counts: counts ?? libraryCounts(0, totalElements),
    items: pageResponse(items, { page, size, totalElements }),
  };
}
