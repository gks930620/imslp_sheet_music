import { useEffect } from "react";

/**
 * 브라우저 탭 제목 — 03_기술결정 §21-7. 라이브러리 없이 `document.title` 대입 하나.
 * 언마운트 시 기본값으로 되돌리지 않는다(다음 화면이 곧바로 자기 제목을 쓴다).
 * 문자열 표는 화면정의 00 §4-1. 아직 모를 때(응답 전)는 빈 값을 넘기면 이전 제목이 남는다.
 */
export function useDocumentTitle(title) {
  useEffect(() => {
    if (title) document.title = title;
  }, [title]);
}
