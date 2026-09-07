import { useEffect, useRef, useState } from "react";

const DEBOUNCE_MS = 300;

/**
 * 관리 목록 화면(작곡가·곡·저작권 대기함)의 검색창 공통 규칙.
 * URL 쿼리가 상태의 원본이므로 바깥 값이 바뀌면 입력칸이 따라가고,
 * 사용자가 친 값만 300ms 뒤 한 번 `onCommit` 으로 넘긴다.
 *
 * @param {string} value    URL 쿼리에서 읽은 현재 검색어
 * @param {(next: string) => void} onCommit 디바운스 뒤 호출 (보통 setSearchParams)
 * @returns {[string, (next: string) => void]} 입력칸 값과 변경 처리기
 */
export function useDebouncedSearchInput(value, onCommit, { delay = DEBOUNCE_MS } = {}) {
  const [text, setText] = useState(value);
  const timerRef = useRef(null);
  const commitRef = useRef(onCommit);

  useEffect(() => {
    commitRef.current = onCommit;
  });

  useEffect(() => {
    setText(value);
  }, [value]);

  useEffect(() => () => clearTimeout(timerRef.current), []);

  const change = (next) => {
    setText(next);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => commitRef.current?.(next), delay);
  };

  return [text, change];
}
