/**
 * 공용 Toast (00_공통 토큰 `--z-toast`, 05·06 관리 화면 문구: 저장했어요 / 삭제했어요 / 판정했어요 …)
 *
 * 저장·삭제 뒤 곧바로 목록으로 이동하는 화면이 많아 Toast 가 **화면 언마운트 뒤에도 남아야** 한다.
 * 그래서 React 트리 안이 아니라 document.body 에 직접 붙였다 스스로 사라지는 방식으로 만든다.
 * (라우팅 밖 전역 호스트를 두면 화면 단위 렌더에서는 호스트가 없어 Toast 가 사라진다)
 */

const AUTO_HIDE_MS = 3_000;

/** 되돌리기 변형은 5초 — 누를 결정을 하는 시간 (00 §3-11, 09 §1-1-2) */
export const TOAST_UNDO_MS = 5_000;

let element = null;
let timer = null;

/** 떠 있는 Toast 를 즉시 내린다 */
export function dismissToast() {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
  element?.remove();
  element = null;
}

/** 변형별 아이콘 (00 §3-11 · 03_기술결정 §29-1) — 중립 변형은 만들지 않는다. 아이콘 없는 줄은 되돌리기뿐이다 */
const VARIANT_ICONS = { success: "check_circle", danger: "error" };

/** material-icons 는 글자(리거처)라 aria-hidden 이 없으면 낭독에 `check_circle` 이 섞인다 (§29-3) */
function materialIcon(name) {
  const icon = document.createElement("span");
  icon.className = "material-icons";
  icon.setAttribute("aria-hidden", "true");
  icon.textContent = name;
  return icon;
}

/**
 * 문구 하나를 띄운다. 이미 떠 있으면 마지막 것으로 바꾼다(쌓지 않는다 — 00 §3-11).
 *
 * @param {string} message 문구
 * @param {object} [options]
 * @param {"success"|"danger"} [options.variant] 아이콘·색 (기본 성공 — §29-1)
 * @param {{label: string, onClick: () => void}} [options.action] 되돌리기 변형 — 오른쪽 텍스트 버튼 하나
 * @param {number} [options.durationMs] 지속 시간(기본 3초, 되돌리기 변형은 5초를 넘긴다)
 *
 * 기존 호출부 `showToast(message)` 는 그대로 동작한다(관리 화면 불변 — 09 F3).
 */
export function showToast(message, { variant = "success", action = null, durationMs } = {}) {
  if (typeof document === "undefined" || !message) return;
  dismissToast();

  element = document.createElement("div");
  element.className = [
    "toast",
    `toast-${VARIANT_ICONS[variant] ? variant : "success"}`,
    action ? "toast-with-action" : "",
  ]
    .filter(Boolean)
    .join(" ");
  element.setAttribute("role", "status");

  // 되돌리기 변형은 아이콘을 그리지 않는다 — 판정이 아니라 중립 통보이고,
  // 480px 한 줄에 [아이콘][문구][되돌리기][닫기] 넷이 서면 문구가 두 줄로 밀린다 (§29-2)
  if (!action) {
    const icon = materialIcon(VARIANT_ICONS[variant] ?? VARIANT_ICONS.success);
    icon.classList.add("toast-icon");
    element.appendChild(icon);
  }

  const text = document.createElement("span");
  text.className = "toast-message";
  text.textContent = message;
  element.appendChild(text);

  if (action) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "toast-action";
    button.textContent = action.label;
    // 되돌리기를 누르면 토스트는 즉시 사라진다 — 되돌아온 항목이 곧 피드백이다(09 §1-1-2)
    button.addEventListener("click", () => {
      dismissToast();
      action.onClick?.();
    });
    element.appendChild(button);
  }

  // 수동 닫기 (00 §3-11) — 이름이 `알림 닫기` 인 이유는 §29-3: 라이트박스의 `닫기` 와 조회가 겹치면 안 된다.
  // 닫기는 되돌리기를 실행하지 않는다 — 닫기 = 그 행동을 그대로 확정한다.
  const close = document.createElement("button");
  close.type = "button";
  close.className = "toast-close";
  close.setAttribute("aria-label", "알림 닫기");
  close.appendChild(materialIcon("close"));
  close.addEventListener("click", () => dismissToast());
  element.appendChild(close);

  document.body.appendChild(element);
  timer = setTimeout(dismissToast, durationMs ?? AUTO_HIDE_MS);
}
