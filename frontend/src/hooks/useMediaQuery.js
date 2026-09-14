import { useCallback, useSyncExternalStore } from "react";

/**
 * CSS 미디어 쿼리 구독 — 자리 문구처럼 CSS 로 바꿀 수 없는 글자를 폭에 따라 고를 때 쓴다 (00 §3-1 짧은 자리 문구).
 * 레이아웃은 여전히 CSS 미디어 쿼리 몫이다. 이 훅은 문자열 선택에만 쓴다.
 */
export function useMediaQuery(query) {
  const subscribe = useCallback(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    [query],
  );
  const getSnapshot = () => window.matchMedia(query).matches;
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
