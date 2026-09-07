/** 00_공통 §3-9 빈 상태 — 아이콘 64px + 제목 + 보조 + (동작) */
export function EmptyState({ icon, title, description, children, className = "" }) {
  return (
    <section className={`empty-state ${className}`.trim()}>
      {icon ? <span className="material-icons">{icon}</span> : null}
      <h3>{title}</h3>
      {description ? <p>{description}</p> : null}
      {children ? <div className="empty-state-actions">{children}</div> : null}
    </section>
  );
}
