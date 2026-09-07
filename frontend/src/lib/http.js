let refreshPromise = null;

async function parseJsonSafely(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

export async function toApiError(response) {
  const body = await parseJsonSafely(response);
  return {
    status: response.status,
    message: body?.message ?? defaultErrorMessage(response.status),
    errorCode: body?.errorCode ?? "UNKNOWN_ERROR",
    errors: body?.errors ?? null,
  };
}

function defaultErrorMessage(status) {
  switch (status) {
    case 400:
      return "잘못된 요청입니다.";
    case 401:
      return "로그인이 필요합니다.";
    case 403:
      return "권한이 없습니다.";
    case 404:
      return "요청한 리소스를 찾을 수 없습니다.";
    case 409:
      return "중복된 요청입니다.";
    case 500:
      return "서버 오류가 발생했습니다.";
    default:
      return "오류가 발생했습니다.";
  }
}

async function tryRefreshToken() {
  // 네트워크 예외도 표준 계약(성공 여부 boolean)으로 흡수한다.
  // (raw Error가 callApi 밖으로 전파되면 표준 에러 객체 원칙을 우회하게 됨)
  try {
    const response = await fetch("/api/tokens/refresh", {
      method: "POST",
      credentials: "include",
    });
    return response.ok;
  } catch {
    return false;
  }
}

/**
 * refresh 실패(세션 만료/폐기) 시 전역으로 알린다.
 * AuthContext가 이 이벤트를 받아 인증 상태를 게스트로 강등한다.
 * (강등 배선이 없으면 "로그인된 것처럼 보이지만 아무 것도 안 되는" 상태가 됨)
 */
function notifyAuthExpired() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("auth:expired"));
  }
}

export async function authFetch(url, options = {}) {
  const mergedOptions = {
    credentials: "include",
    ...options,
  };

  let response = await fetch(url, mergedOptions);
  if (response.status !== 401) {
    return response;
  }

  const errorBody = await parseJsonSafely(response.clone());
  if (errorBody?.errorCode !== "TOKEN_EXPIRED") {
    return response;
  }

  if (!refreshPromise) {
    refreshPromise = tryRefreshToken().finally(() => {
      refreshPromise = null;
    });
  }

  const refreshed = await refreshPromise;
  if (!refreshed) {
    notifyAuthExpired();
    return response;
  }

  return fetch(url, mergedOptions);
}

export async function callApi(url, options = {}) {
  const response = await authFetch(url, options);
  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return { success: true };
  }

  const body = await parseJsonSafely(response);
  return body ?? { success: true };
}

/**
 * 파일 업로드 공통 헬퍼 (FormData + POST /api/files)
 * @param {File[]} files 업로드할 파일 목록
 * @param {number|string} refId 참조 ID (임시 업로드는 0)
 * @param {"THUMBNAIL"|"IMAGES"|"ATTACHMENT"} usage 용도
 * @param {"COMMUNITY"|"USER"} [refType="COMMUNITY"] 참조 타입
 * @returns {Promise<string[]>} 업로드된 파일 경로(URL) 목록
 */
export async function uploadFiles(files, refId, usage, refType = "COMMUNITY") {
  if (!files.length) return [];

  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));
  formData.append("refId", String(refId));
  formData.append("refType", refType);
  formData.append("usage", usage);

  const response = await authFetch("/api/files", { method: "POST", body: formData });
  if (!response.ok) {
    throw await toApiError(response);
  }
  const body = await parseJsonSafely(response);
  return body?.data ?? [];
}

export async function callPublicApi(url, options = {}) {
  const response = await fetch(url, { credentials: "include", ...options });
  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return { success: true };
  }

  const body = await parseJsonSafely(response);
  return body ?? { success: true };
}

/**
 * 판본 PDF 업로드 (multipart, 필드명 file) — 02_API 명세서 §5-1 / §7.
 * 기존 uploadFiles 는 커뮤니티 첨부(/api/files) 전용이라 판본용을 따로 둔다.
 * @param {File} file
 * @returns {Promise<{fileId:number, previewFileId:number|null, fileName:string, fileSize:number, pageCount:number|null, previewUrl:string|null}>}
 */
export async function uploadEditionFile(file) {
  const formData = new FormData();
  formData.append("file", file);

  const response = await authFetch("/api/admin/edition-files", { method: "POST", body: formData });
  if (!response.ok) {
    throw await toApiError(response);
  }
  const body = await parseJsonSafely(response);
  return body?.data ?? null;
}
