export function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return date.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatRelativeTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";

  const now = Date.now();
  const diff = now - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;
  if (hours < 24) return `${hours}시간 전`;
  if (days < 7) return `${days}일 전`;
  return date.toLocaleDateString("ko-KR");
}

export function formatFileSize(bytes) {
  if (!bytes || Number.isNaN(bytes)) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function getErrorMessage(error, fallback = "요청 처리 중 오류가 발생했습니다.") {
  if (!error) return fallback;
  if (typeof error === "string") return error;
  return error.message ?? fallback;
}

/* =====================================================================
   쉬운악보 화면 표기 (00_공통_레이아웃_토큰.md §3-3·§3-4·§4, 04, 07)
   ===================================================================== */

const KB = 1024;
const MB = 1024 * 1024;

/** 악보 화면 파일 크기: 1MB 이상 "2.4MB", 미만 "640KB". 값이 없으면 빈 문자열 */
export function formatFileSizeCompact(bytes) {
  if (bytes === null || bytes === undefined || Number.isNaN(Number(bytes))) return "";
  if (bytes >= MB) return `${(bytes / MB).toFixed(1)}MB`;
  return `${Math.max(1, Math.round(bytes / KB))}KB`;
}

const LEVEL_LABELS = {
  BEGINNER: "입문",
  ELEMENTARY: "초급",
  INTERMEDIATE: "중급",
  ADVANCED: "고급",
};

/** 난이도 칩 문구. 미정이면 "난이도 미정" */
export function formatLevel(level) {
  return LEVEL_LABELS[level] ?? "난이도 미정";
}

const WORK_STATUS_LABELS = {
  READY: null,
  PREPARING: "준비 중",
  RESTRICTED: "한국에서 이용 제한",
  UNKNOWN: "저작권 확인 중",
};

/** 곡 상태 뱃지 문구. READY 는 뱃지 없음(null) */
export function formatWorkStatus(status) {
  return WORK_STATUS_LABELS[status] ?? null;
}

const COPYRIGHT_LABELS = {
  FREE: "한국에서 자유 이용 가능",
  RESTRICTED: "한국에서 이용 제한",
  UNKNOWN: "저작권 확인 중",
};

/** 판본 저작권 뱃지 문구 */
export function formatCopyright(koreaCopyright) {
  return COPYRIGHT_LABELS[koreaCopyright] ?? COPYRIGHT_LABELS.UNKNOWN;
}

const EDITION_KIND_LABELS = {
  COMPLETE_SCORE: "전체 악보",
  PARTS: "파트보",
  ARRANGEMENT: "편곡",
};

/** 판본 종류 (원어 표기 금지) */
export function formatEditionKind(kind) {
  return EDITION_KIND_LABELS[kind] ?? "";
}

/** 판본 포함 범위: 전곡 / N악장만 / (번호 없으면) IMSLP 섹션명 */
export function formatEditionScope(edition) {
  if (!edition) return "";
  if (edition.scope === "MOVEMENT") {
    if (edition.movementNumber) return `${edition.movementNumber}악장만`;
    return edition.sectionLabel ?? "";
  }
  return "전곡";
}

/** 생몰년 "1843–1907" / "1843–" / "" (en dash) */
export function formatLifeSpan(birthYear, deathYear) {
  if (!birthYear) return "";
  return `${birthYear}–${deathYear ?? ""}`;
}

function splitMinutes(seconds) {
  const minutes = Math.floor(seconds / 60);
  return { minutes, hours: Math.floor(minutes / 60), restMinutes: minutes % 60 };
}

/** 예상 소요 "약 1시간 10분" / "약 12분" / "1분 이내". 값이 없으면 null */
export function formatEstimatedTime(seconds) {
  if (seconds === null || seconds === undefined) return null;
  const { minutes, hours, restMinutes } = splitMinutes(seconds);
  if (minutes < 1) return "1분 이내";
  if (hours < 1) return `약 ${minutes}분`;
  return restMinutes > 0 ? `약 ${hours}시간 ${restMinutes}분` : `약 ${hours}시간`;
}

/** 걸린 시간 "1시간 3분" / "12분" / "1분 이내" */
export function formatElapsedTime(seconds) {
  if (seconds === null || seconds === undefined) return "";
  const { minutes, hours, restMinutes } = splitMinutes(seconds);
  if (minutes < 1) return "1분 이내";
  if (hours < 1) return `${minutes}분`;
  return restMinutes > 0 ? `${hours}시간 ${restMinutes}분` : `${hours}시간`;
}

const CRAWL_JOB_STATUS_LABELS = {
  RUNNING: "진행 중",
  PAUSED: "일시 정지",
  COMPLETED: "완료",
  STOPPED: "중지됨",
  FAILED: "실패",
};

/** 수집 작업 상태 문구 */
export function formatCrawlJobStatus(status) {
  return CRAWL_JOB_STATUS_LABELS[status] ?? "";
}

function toDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function pad(value) {
  return String(value).padStart(2, "0");
}

/** 관리 화면 일시 "2026-09-06 14:02" */
export function formatAdminDateTime(value) {
  const date = toDate(value);
  if (!date) return "-";
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 관리 목록 일시 "09-06 14:02" */
export function formatShortDateTime(value) {
  const date = toDate(value);
  if (!date) return "-";
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 시각 "14:35" (options.seconds 면 "14:35:10") */
export function formatClockTime(value, { seconds = false } = {}) {
  const date = toDate(value);
  if (!date) return "-";
  const base = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  return seconds ? `${base}:${pad(date.getSeconds())}` : base;
}

/** 천 단위 쉼표 */
export function formatCount(value) {
  return Number(value ?? 0).toLocaleString("en-US");
}

function startOfLocalDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

/**
 * 받은 악보의 날짜 한 줄 (00_공통 §4 · 화면정의 09 §1-2-1).
 * `오늘 받음` / `어제 받음` / `9월 12일 받음` / `2025년 9월 12일 받음`.
 *
 * 시·분은 쓰지 않는다 — 선반이지 영수증이 아니다(기획 05 §3-2). 갈리는 기준은 **사용자 지역 시각의 달력 날짜**다.
 * `now` 를 인자로 받는 이유: 시간에 기대는 로직을 결정적으로 시험하기 위해서다(컨벤션 §6).
 *
 * @param {string|null} value 서버가 주는 ISO UTC 문자열
 * @param {Date} [now] 지금. 값이 없거나 이상하면 빈 문자열 — 화면은 줄을 만들지 않는다
 */
export function formatReceivedDate(value, now = new Date()) {
  const date = toDate(value);
  if (!date) return "";

  const days = Math.round((startOfLocalDay(now) - startOfLocalDay(date)) / 86400000);
  if (days === 0) return "오늘 받음";
  if (days === 1) return "어제 받음";

  const month = date.getMonth() + 1;
  const sameYear = date.getFullYear() === now.getFullYear();
  return sameYear
    ? `${month}월 ${date.getDate()}일 받음`
    : `${date.getFullYear()}년 ${month}월 ${date.getDate()}일 받음`;
}

/**
 * 판본 한 줄 설명 (02 §2-4 EditionBriefDTO · 화면정의 09 §1-2 D7).
 * `전체 악보 · 전곡 · 5쪽 · 1.1MB` — **있는 것만** ` · ` 로. 서버는 문장을 만들지 않는다(§2-2-1 과 같은 원칙).
 */
export function formatEditionBrief(brief) {
  if (!brief) return "";
  // 02 §2-4 — 스냅샷에는 `sectionLabel` 이 없다. 악장 번호를 모르는 발췌는 `발췌` 로 폴백한다(§3-4 접미사 규칙과 같은 말)
  const scope = formatEditionScope(brief) || (brief.scope === "MOVEMENT" ? "발췌" : "");
  return [
    formatEditionKind(brief.kind),
    scope,
    brief.pageCount ? `${brief.pageCount}쪽` : "",
    formatFileSizeCompact(brief.fileSize),
  ]
    .filter(Boolean)
    .join(" · ");
}

/* ---------------------------------------------------------------------
   받게 되는 악보의 범위 한 줄 (02 §2-2-1 scopeNote, 기획 §2 F2-5 · §11-2)
   별칭 일치 줄과 같은 자리·같은 한 줄이라 여기서 함께 만든다.
   --------------------------------------------------------------------- */

/**
 * 묶음 악보인가 (02 §2-2-1 COLLECTION 의 두 번째 쓰임).
 * "COLLECTION" 코드 문자열의 단일 출처 — 별칭 문구와 '(전곡 기준)' 꼬리표가 같은 판정을 쓴다.
 */
export function isCollectionWork(scopeNote) {
  return (scopeNote?.codes ?? []).includes("COLLECTION");
}

/** MOVEMENT_ONLY 문구. 수집이 악장 번호를 못 읽었으면 폴백(발췌) */
function movementOnlyText(movementNumber) {
  return movementNumber ? `${movementNumber}악장만 들어 있어요` : "일부 악장만 들어 있어요";
}

/**
 * 곡 카드의 "별칭 · 범위" 한 줄. 만들 말이 없으면 빈 문자열.
 * 묶음(COLLECTION)은 별칭과 묶여 "'X'가 들어 있는 악보" 로 '…으로 찾음' 을 대체한다.
 */
export function formatWorkScopeLine({ matchedAlias = null, scopeNote = null } = {}) {
  const codes = scopeNote?.codes ?? [];
  const isCollection = isCollectionWork(scopeNote);
  const parts = [];

  if (matchedAlias) {
    parts.push(isCollection ? `'${matchedAlias}'가 들어 있는 악보` : `'${matchedAlias}'으로 찾음`);
  }
  if (codes.includes("ARRANGEMENT")) parts.push("피아노 편곡 악보예요");
  if (codes.includes("MOVEMENT_ONLY")) parts.push(movementOnlyText(scopeNote?.movementNumber));

  return parts.join(" · ");
}

const AUTO_JUDGE_SKIP_REASON_LABELS = {
  LICENSE_NOT_REDISTRIBUTABLE: "재배포 허용 라이선스가 아님",
  COMPOSER_DEATH_YEAR_UNKNOWN: "작곡가 몰년을 모름",
  COMPOSER_COPYRIGHT_ACTIVE: "작곡가 사후 70년 미경과",
  PUBLICATION_TOO_RECENT: "출판 70년 미경과",
  EDITOR_UNVERIFIABLE: "편집자 생몰 확인 필요",
};

/**
 * 자동 판정이 이 판본을 열지 못한 이유(02 §5-8 autoJudgeSkipReason · §5-11 skipped[].reason).
 * 대기함 행과 자동 판정 미리보기 모달이 함께 쓰는 단일 출처다.
 *
 * 폴백이 다른 함수들과 다르다: 값이 없으면 빈 문자열(자동으로 열릴 판본이라 할 말이 없다),
 * 모르는 코드는 코드를 그대로 돌려준다 — 삼키면 관리자의 일감이 설명 없이 사라진다.
 */
export function formatAutoJudgeSkipReason(reason) {
  if (!reason) return "";
  return AUTO_JUDGE_SKIP_REASON_LABELS[reason] ?? reason;
}

/** 추천 지정 경고(02 §5-6) — 판본의 악장 번호는 화면이 이미 가진 데이터에서 쓴다 */
export function formatRecommendWarning(code, edition) {
  if (code === "NOT_DOWNLOADABLE") {
    return "이 판본은 사용자에게 다운로드가 열리지 않아요 — 저작권 판정을 '자유 이용 가능'으로 바꿔야 해요";
  }
  if (code === "ARRANGEMENT") return "이 판본은 편곡이에요. 사용자가 원곡 악보를 기대하고 받을 수 있어요";
  if (code === "PARTIAL_SCOPE") {
    const scope = edition?.movementNumber ? `${edition.movementNumber}악장만` : "일부만";
    return `이 판본은 ${scope} 들어 있어요. 곡 전체가 아니에요`;
  }
  return "";
}
