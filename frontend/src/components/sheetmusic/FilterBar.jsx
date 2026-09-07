const LEVELS = [
  { value: "BEGINNER", label: "입문", hint: "바이엘 수준" },
  { value: "ELEMENTARY", label: "초급", hint: "체르니 30 수준" },
  { value: "INTERMEDIATE", label: "중급", hint: "체르니 40·소나티네 수준" },
  { value: "ADVANCED", label: "고급", hint: "체르니 50 이상·연주회 레퍼토리" },
];

const PAGE_RANGES = [
  { value: "LE10", label: "10쪽 이하" },
  { value: "11_20", label: "11~20쪽" },
  { value: "GE21", label: "21쪽 이상" },
];

/**
 * 00_공통 §3-13 필터 바 — 난이도 복수 / 쪽수 단일 / 바로 받기 체크박스.
 * 선택 즉시 URL 쿼리를 갱신하는 건 화면(부모) 책임. 여기서는 다음 값만 알려준다.
 */
export function FilterBar({ levels = [], pages = null, downloadable = false, onChange }) {
  const hasFilter = levels.length > 0 || Boolean(pages) || downloadable;

  const toggleLevel = (value) => {
    const next = levels.includes(value) ? levels.filter((item) => item !== value) : [...levels, value];
    const ordered = LEVELS.map((level) => level.value).filter((value2) => next.includes(value2));
    onChange({ levels: ordered, pages, downloadable });
  };

  return (
    <div className="filter-bar">
      <div className="filter-group">
        <span className="filter-group-label">난이도</span>
        {LEVELS.map((level) => (
          <button
            key={level.value}
            className={`filter-chip${levels.includes(level.value) ? " active" : ""}`}
            type="button"
            title={level.hint}
            aria-pressed={levels.includes(level.value)}
            onClick={() => toggleLevel(level.value)}
          >
            {level.label}
          </button>
        ))}
      </div>

      <div className="filter-group">
        <span className="filter-group-label">쪽수</span>
        {PAGE_RANGES.map((range) => (
          <button
            key={range.value}
            className={`filter-chip${pages === range.value ? " active" : ""}`}
            type="button"
            aria-pressed={pages === range.value}
            onClick={() => onChange({ levels, pages: pages === range.value ? null : range.value, downloadable })}
          >
            {range.label}
          </button>
        ))}
      </div>

      <label className="filter-check">
        <input
          type="checkbox"
          checked={downloadable}
          onChange={(event) => onChange({ levels, pages, downloadable: event.target.checked })}
        />
        <span>바로 받기 가능한 곡만</span>
      </label>

      {hasFilter ? (
        <button
          className="btn btn-text filter-clear"
          type="button"
          onClick={() => onChange({ levels: [], pages: null, downloadable: false })}
        >
          필터 해제
        </button>
      ) : null}
    </div>
  );
}
