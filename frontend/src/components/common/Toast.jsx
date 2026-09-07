/**
 * 공용 Toast (00_공통 토큰 `--z-toast`, 05·06 관리 화면 문구: 저장했어요 / 삭제했어요 / 판정했어요 …)
 *
 * 저장·삭제 뒤 곧바로 목록으로 이동하는 화면이 많아 Toast 가 **화면 언마운트 뒤에도 남아야** 한다.
 * 그래서 React 트리 안이 아니라 document.body 에 직접 붙였다 스스로 사라지는 방식으로 만든다.
 * (라우팅 밖 전역 호스트를 두면 화면 단위 렌더에서는 호스트가 없어 Toast 가 사라진다)
 */

const AUTO_HIDE_MS = 3_000;

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

/** 문구 하나를 띄운다. 이미 떠 있으면 마지막 것으로 바꾼다 */
export function showToast(message) {
  if (typeof document === "undefined" || !message) return;
  dismissToast();

  element = document.createElement("div");
  element.className = "toast";
  element.setAttribute("role", "status");
  element.textContent = message;
  document.body.appendChild(element);

  timer = setTimeout(dismissToast, AUTO_HIDE_MS);
}
