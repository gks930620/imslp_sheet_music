import { useCallback } from "react";
import { useLocation } from "react-router-dom";
import { linkSection, sectionFromPathname } from "../lib/sections.js";

/**
 * 현재 악기 구분 — 03_기술결정 §21-6: 유일한 근원은 주소(경로 첫 세그먼트)다.
 * 쿠키·localStorage 에 저장하지 않고, 하위 컴포넌트에 prop 으로 내려보내지도 않는다 — 필요한 곳이 이 훅을 부른다.
 *
 * @returns 구분 객체(`lib/sections.js`) 또는 null(구분 밖 — 관리·로그인·없는 구분 이름·옛 주소)
 */
export function useSection() {
  const { pathname } = useLocation();
  return sectionFromPathname(pathname);
}

/**
 * 구분 안 화면으로 가는 링크를 만드는 함수 — 02 §0-5 링크 규칙(현재 구분이 없거나 준비 중이면 기본 구분).
 *
 * @returns (subpath?: string) => string  예: path("/works/21") → "/piano/works/21"
 */
export function useSectionPath() {
  const section = useSection();
  const slug = linkSection(section).slug;
  return useCallback((subpath = "") => `/${slug}${subpath}`, [slug]);
}
