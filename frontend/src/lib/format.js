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
