import { formatWorkStatus } from "../../lib/format.js";

const BADGE = {
  PREPARING: { className: "badge-preparing", icon: "hourglass_empty" },
  RESTRICTED: { className: "badge-restricted", icon: "block" },
  UNKNOWN: { className: "badge-unknown", icon: "help" },
};

/** 00_공통 §3-4 곡 상태 뱃지 — READY 는 뱃지 없음 */
export function StatusBadge({ status }) {
  const label = formatWorkStatus(status);
  if (!label) return null;
  const badge = BADGE[status] ?? BADGE.UNKNOWN;
  return (
    <span className={`status-badge ${badge.className}`}>
      <span className="material-icons" aria-hidden="true">
        {badge.icon}
      </span>
      {label}
    </span>
  );
}
