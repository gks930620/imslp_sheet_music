import { useRef } from "react";
import { SEARCH_SCOPES, normalizeScope } from "../../lib/searchScope.js";

const KEY_DELTA = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };

/**
 * 00_공통 §3-16 · 08 §4-1 검색 기준 선택 — 검색창 바로 위 세그먼트 3칸(라디오 그룹).
 * - 드롭다운(select)이 아니다: 세 선택지가 누르기 전에 다 보여야 한다 (08 §4-2).
 * - 항상 정확히 하나가 선택돼 있다. 알 수 없는 값은 "전체"로 보인다 — 오류 문구를 만들지 않는다 (인수 조건 8-F 4).
 * - 이미 선택된 칸을 다시 눌러도 알리지 않는다 (같은 검색을 두 번 하지 않는다).
 * - 키보드: Tab 으로 그룹 진입(선택 칸만 포커스), 좌우 화살표로 이동하며 즉시 선택 (라디오 그룹 표준).
 *
 * @param {string} value    "ALL" | "TITLE" | "COMPOSER" (그 밖은 전체로 본다)
 * @param {(scope: string) => void} onChange  고른 계약값(대문자)
 */
export function SearchScopeSelect({ value, onChange, className = "" }) {
  const current = normalizeScope(value);
  const buttons = useRef([]);

  const select = (index) => {
    const next = SEARCH_SCOPES[index].value;
    buttons.current[index]?.focus();
    if (next !== current) onChange(next);
  };

  const handleKeyDown = (event, index) => {
    const delta = KEY_DELTA[event.key];
    if (!delta) return;
    event.preventDefault();
    select((index + delta + SEARCH_SCOPES.length) % SEARCH_SCOPES.length);
  };

  return (
    <div className={`scope-select ${className}`.trim()} role="radiogroup" aria-label="검색 기준">
      {SEARCH_SCOPES.map((scope, index) => {
        const checked = scope.value === current;
        return (
          <button
            key={scope.value}
            ref={(element) => {
              buttons.current[index] = element;
            }}
            className={`scope-select-option${checked ? " selected" : ""}`}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            onClick={() => select(index)}
            onKeyDown={(event) => handleKeyDown(event, index)}
          >
            {scope.label}
          </button>
        );
      })}
    </div>
  );
}
