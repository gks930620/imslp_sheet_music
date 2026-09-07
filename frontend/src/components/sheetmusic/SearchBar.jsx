import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

const PLACEHOLDER = {
  large: "곡 이름, 작곡가, 작품번호로 찾기 — 예: 월광, 쇼팽 녹턴, K.545",
  compact: "곡 이름, 작곡가, 작품번호",
};

/**
 * 00_공통 §3-1 검색창.
 * large: 홈·검색 결과·찾을 수 없음 / compact: 헤더
 * 공백만 입력하면 이동하지 않고 입력칸만 흔들린다 (오류 문구 없음).
 */
export function SearchBar({ variant = "large", initialValue = "", autoFocus = false, className = "" }) {
  const navigate = useNavigate();
  const inputRef = useRef(null);
  const [value, setValue] = useState(initialValue);
  const [shaking, setShaking] = useState(false);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  useEffect(() => {
    if (autoFocus) inputRef.current?.focus();
  }, [autoFocus]);

  const submit = (event) => {
    event.preventDefault();
    const keyword = value.trim();
    if (!keyword) {
      setShaking(true);
      window.setTimeout(() => setShaking(false), 400);
      inputRef.current?.focus();
      return;
    }
    navigate(`/search?q=${encodeURIComponent(keyword)}`);
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
          placeholder={PLACEHOLDER[variant] ?? PLACEHOLDER.large}
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
