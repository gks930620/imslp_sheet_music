const ICONS = {
  info: "info",
  warning: "warning",
  danger: "error",
  success: "check_circle",
  restricted: "block",
};

/** 00_공통 §3-11 알림 상자 — 화면 안에 고정, 사라지지 않음 */
export function InlineAlert({ variant = "info", icon, children, className = "" }) {
  return (
    <div className={`inline-alert inline-alert-${variant} ${className}`.trim()} role="status">
      <span className="material-icons" aria-hidden="true">
        {icon ?? ICONS[variant] ?? ICONS.info}
      </span>
      <div className="inline-alert-body">{children}</div>
    </div>
  );
}
