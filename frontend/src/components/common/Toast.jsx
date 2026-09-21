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

/**
 * 문구 하나를 띄운다. 이미 떠 있으면 마지막 것으로 바꾼다(쌓지 않는다 — 00 §3-11).
 *
 * @param {string} message 문구
 * @param {object} [options]
 * @param {{label: string, onClick: () => void}} [options.action] 되돌리기 변형 — 오른쪽 텍스트 버튼 하나
 * @param {number} [options.durationMs] 지속 시간(기본 3초, 되돌리기 변형은 5초를 넘긴다)
 *
 * 기존 호출부 `showToast(message)` 는 그대로 동작한다(관리 화면 불변 — 09 F3).
 */
export function showToast(message, { action = null, durationMs } = {}) {
  if (typeof document === "undefined" || !message) return;
  dismissToast();

  element = document.createElement("div");
  element.className = action ? "toast toast-with-action" : "toast";
  element.setAttribute("role", "status");

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

  document.body.appendChild(element);
  timer = setTimeout(dismissToast, durationMs ?? AUTO_HIDE_MS);
}
