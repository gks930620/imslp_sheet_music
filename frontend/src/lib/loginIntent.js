/**
 * 로그인 왕복 너머로 "무엇을 하려 했는가" 를 들고 가는 한 칸 — 03_기술결정 §22, 02 §10-4.
 *
 * 저장소가 `sessionStorage` 인 것이 계약이다: 카카오·구글은 우리 오리진을 떠났다가 새 문서로 돌아오므로
 * 주소의 `?redirect=` 가 사라지지만(OAuth2LoginSuccessHandler 가 "/" 로 보낸다) 같은 탭의 sessionStorage 는 남는다.
 * `localStorage` 는 공용 컴퓨터에서 몇 시간 뒤 다른 사람의 탭에 되살아나므로 쓰지 않는다.
 *
 * 소비는 **take**(읽는 즉시 지운다)다 — 요청을 보내기 전에 지워야 새로고침에 소비할 것이 남지 않는다(인수 조건 8-B 4).
 */

export const LOGIN_INTENT_KEY = "sheetmusic.loginIntent";

/** 10분. 탭을 며칠 열어 둔 사람이 엉뚱한 순간에 즐겨찾기되는 일을 막는다 (03 §22-2) */
export const LOGIN_INTENT_TTL_MS = 10 * 60 * 1000;

/** 로그인 화면의 "이유 한 줄" 쿼리 값 (02 §10-4 — 화면 전용, 서버는 본 적이 없다) */
export const LOGIN_REASON = { FAVORITE: "favorite", LIBRARY: "library" };

function readRaw() {
  try {
    return sessionStorage.getItem(LOGIN_INTENT_KEY);
  } catch {
    return null; // 저장소가 막힌 환경 — 의도가 없는 것과 같다
  }
}

/** 저장 실패·삭제 실패는 전부 삼킨다. 의도는 편의이지 기능의 전제가 아니다 */
export function clearLoginIntent() {
  try {
    sessionStorage.removeItem(LOGIN_INTENT_KEY);
  } catch {
    /* 무시 */
  }
}

function read({ consume }) {
  const raw = readRaw();
  if (!raw) return null;

  let parsed = null;
  try {
    parsed = JSON.parse(raw);
  } catch {
    clearLoginIntent(); // 값이 깨졌으면 버린다 — 오류를 던지지 않는다
    return null;
  }

  const fresh =
    parsed &&
    typeof parsed === "object" &&
    typeof parsed.returnTo === "string" &&
    typeof parsed.at === "number" &&
    Date.now() - parsed.at <= LOGIN_INTENT_TTL_MS;
  if (!fresh) {
    clearLoginIntent();
    return null;
  }

  if (consume) clearLoginIntent();
  return {
    action: parsed.action ?? null,
    workId: parsed.workId ?? null,
    returnTo: parsed.returnTo,
    at: parsed.at,
  };
}

/**
 * 의도를 적어 둔다.
 * @param {{action?: "FAVORITE"|null, workId?: number|null, returnTo: string}} intent
 */
export function saveLoginIntent({ action = null, workId = null, returnTo }) {
  if (typeof returnTo !== "string" || !returnTo) return;
  try {
    sessionStorage.setItem(
      LOGIN_INTENT_KEY,
      JSON.stringify({ action: action ?? null, workId: workId ?? null, returnTo, at: Date.now() }),
    );
  } catch {
    /* 무시 */
  }
}

/** 보기만 한다 — 주소 점프(03 §22-2)가 쓴다. 소비는 그 곡의 곡 상세 하나뿐이다 */
export function peekLoginIntent() {
  return read({ consume: false });
}

/** 읽는 즉시 지운다 — 실행은 그 다음이다(8-B 4) */
export function takeLoginIntent() {
  return read({ consume: true });
}

/**
 * 로그인 화면 주소 (02 §10-4) — 복귀는 기존 `redirect`, 이유는 화면 전용 `reason`.
 * @param {string} returnTo 돌아갈 주소(쿼리 포함)
 * @param {string} [reason] LOGIN_REASON 값. 없으면 기존 부제 그대로다
 */
export function loginHref(returnTo, reason) {
  const base = `/login?redirect=${encodeURIComponent(returnTo)}`;
  return reason ? `${base}&reason=${reason}` : base;
}
