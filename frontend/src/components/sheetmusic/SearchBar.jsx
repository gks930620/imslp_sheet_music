import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSectionPath } from "../../hooks/useSection.js";
import { useMediaQuery } from "../../hooks/useMediaQuery.js";
import { DEFAULT_SCOPE, buildSearchHref, scopePlaceholder } from "../../lib/searchScope.js";

/** 헤더(compact)의 자리 문구는 기준과 무관하게 고정 — 헤더는 항상 "전체"다 (08 §4-3·§4-6 D7) */
const COMPACT_PLACEHOLDER = "곡 이름, 작곡가, 작품번호";
/** 00 §3-1 — 이 폭 아래에서는 긴 자리 문구가 잘리므로 짧은 버전(08 §4-3)을 쓴다. 검색창 높이 분기(600px)와 같은 선 */
const NARROW_QUERY = "(max-width: 599px)";

/**
 * 00_공통 §3-1 검색창.
 * large: 홈·검색 결과·찾을 수 없음 / compact: 헤더
 * 공백만 입력하면 이동하지 않고 입력칸만 흔들린다 (오류 문구 없음).
 * 이동 주소는 현재 구분의 검색 결과(`/{구분}/search`, 02 §0-5) — 구분은 주소에서 읽는다(03 §21-6).
 *
 * @param scope large 에서 선택된 검색 기준(ALL|TITLE|COMPOSER). 제출 시 `in` 으로 실린다(전체면 생략). compact 는 항상 전체
 * @param from  준비 중 구분의 헤더 검색이 붙이는 화면 전용 쿼리(`violin`|`orchestra`, 08 §2-4)
 */
export function SearchBar({
  variant = "large",
  initialValue = "",
  autoFocus = false,
  className = "",
  scope = DEFAULT_SCOPE,
  from,
}) {
  const navigate = useNavigate();
  const sectionPath = useSectionPath();
  const narrow = useMediaQuery(NARROW_QUERY);
  const inputRef = useRef(null);
  const [value, setValue] = useState(initialValue);
  const [shaking, setShaking] = useState(false);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  useEffect(() => {
    if (autoFocus) inputRef.current?.focus();
  }, [autoFocus]);

  const isCompact = variant === "compact";
  const placeholder = isCompact ? COMPACT_PLACEHOLDER : scopePlaceholder(scope, narrow);

  const submit = (event) => {
    event.preventDefault();
    const keyword = value.trim();
    if (!keyword) {
      setShaking(true);
      window.setTimeout(() => setShaking(false), 400);
      inputRef.current?.focus();
      return;
    }
    navigate(buildSearchHref(sectionPath("/search"), { q: keyword, scope: isCompact ? DEFAULT_SCOPE : scope, from }));
  };

  return (
    <form className={`search-bar search-bar-${variant} ${className}`.trim()} role="search" onSubmit={submit}>
      <div className={`search-bar-field${shaking ? " shaking" : ""}`}>
        <span className="material-icons search-bar-icon" aria-hidden="true">
          search
        </span>
        <input
          ref={inputRef}
          className="search-bar-input"
          type="text"
          value={value}
          placeholder={placeholder}
          onChange={(event) => setValue(event.target.value)}
        />
        {value ? (
          <button
            className="search-bar-clear"
            type="button"
            aria-label="지우기"
            onClick={() => {
              setValue("");
              inputRef.current?.focus();
            }}
          >
            <span className="material-icons" aria-hidden="true">
              close
            </span>
          </button>
        ) : null}
      </div>
      {variant === "large" ? (
        <button className="btn btn-primary search-bar-submit" type="submit">
          <span className="material-icons" aria-hidden="true">
            search
          </span>
          <span className="search-bar-submit-text">검색</span>
        </button>
      ) : null}
    </form>
  );
}
