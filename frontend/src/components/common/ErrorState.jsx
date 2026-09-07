/** 00_공통 §3-9 불러오기 실패 — 문구 고정, "다시 시도" 는 같은 요청 재실행 */
export function ErrorState({ onRetry, compact = false }) {
  return (
    <section className={compact ? "error-state error-state-compact" : "error-state"}>
      {compact ? null : <span className="material-icons">wifi_off</span>}
      <h3>연결을 확인해 주세요</h3>
      <p>잠시 후 다시 시도해 주세요</p>
      {onRetry ? (
        <button className="btn btn-outline" type="button" onClick={onRetry}>
          다시 시도
        </button>
      ) : null}
    </section>
  );
}
