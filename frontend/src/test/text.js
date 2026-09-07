import { waitFor } from "@testing-library/react";
import { expect } from "vitest";

/**
 * 문구 검증 헬퍼.
 * 화면 정의서 문구는 `'녹턴' 검색 결과 21곡` 처럼 강조 span 으로 쪼개질 수 있어
 * getByText(직접 텍스트 노드만 비교) 대신 body 전체 텍스트로 확인한다.
 * 주의: 접힌 영역 등 "안 보여야 하는" 내용은 CSS 로 숨기지 말고 렌더하지 않아야 expectNoText 가 성립한다.
 */
export function bodyText() {
  return (document.body.textContent ?? "").replace(/\s+/g, " ");
}

export function expectText(text) {
  expect(bodyText()).toContain(text);
}

export function expectNoText(text) {
  expect(bodyText()).not.toContain(text);
}

export async function findText(text) {
  await waitFor(() => expect(bodyText()).toContain(text));
}

export async function waitForNoText(text) {
  await waitFor(() => expect(bodyText()).not.toContain(text));
}
