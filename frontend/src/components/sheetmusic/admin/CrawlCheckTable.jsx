import { Link } from "react-router-dom";

const VERDICTS = {
  NEW: { label: "수집 예정", icon: "check_circle", className: "verdict-new" },
  ATTACH: { label: "수집 예정", icon: "check_circle", className: "verdict-new" },
  EXISTS: { label: "이미 있음 — 건너뜀", icon: "remove_circle_outline", className: "verdict-exists" },
  INVALID_URL: { label: "주소 형식 오류", icon: "error", className: "verdict-invalid" },
  DUPLICATE: { label: "중복 제거", icon: "content_copy", className: "verdict-duplicate" },
};

const REFRESH_VERDICT = { label: "정보만 갱신", icon: "refresh", className: "verdict-refresh" };

/**
 * 07_관리자_수집.md 화면 A 판정 표.
 * refreshSeqs 에 든 seq 는 "정보만 다시 가져오기" 가 켜진 것 (판정 문구가 바뀐다).
 */
export function CrawlCheckTable({ items = [], refreshSeqs = [], onToggleRefresh }) {
  return (
    <div className="data-table crawl-check-table">
      <div className="data-table-head" aria-hidden="true">
        <span>#</span>
        <span>주소</span>
        <span>판정</span>
      </div>
      {items.map((item) => {
        const refreshing = refreshSeqs.includes(item.seq);
        const verdict = refreshing && item.verdict === "EXISTS" ? REFRESH_VERDICT : VERDICTS[item.verdict];
        return (
          <div key={item.seq} className="data-table-row" data-testid={`crawl-check-row-${item.seq}`}>
            <span className="crawl-check-seq">{item.seq}</span>
            <span className="crawl-check-url" title={item.inputUrl}>
              {item.inputUrl}
              {item.verdict === "DUPLICATE" && item.duplicateOfSeq ? (
                <span className="crawl-check-duplicate-of">{` (${item.duplicateOfSeq}번과 같음)`}</span>
              ) : null}
            </span>
            <span className={`crawl-check-verdict ${verdict?.className ?? ""}`}>
              <span className="crawl-check-verdict-main">
                <span className="material-icons" aria-hidden="true">
                  {verdict?.icon}
                </span>
                {verdict?.label}
              </span>
              {item.verdict === "ATTACH" ? (
                <Link
                  className="crawl-check-attach"
                  to={`/admin/works/${item.existingWorkId}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  등록된 곡에 판본 붙임
                </Link>
              ) : null}
              {item.verdict === "EXISTS" ? (
                <>
                  <label className="crawl-check-refresh">
                    <input
                      type="checkbox"
                      checked={refreshing}
                      onChange={(event) => onToggleRefresh?.(item.seq, event.target.checked)}
                    />
                    <span>정보만 다시 가져오기</span>
                  </label>
                  {refreshing ? <span className="form-help">이미 받은 파일은 다시 받지 않아요</span> : null}
                </>
              ) : null}
            </span>
          </div>
        );
      })}
    </div>
  );
}
