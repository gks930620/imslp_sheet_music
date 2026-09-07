import { formatCopyright } from "../../lib/format.js";

const BADGE = {
  FREE: { className: "badge-free", icon: "check_circle" },
  RESTRICTED: { className: "badge-restricted", icon: "block" },
  UNKNOWN: { className: "badge-unknown", icon: "help" },
};

/** 00_공통 §3-4 저작권 뱃지 3종 (곡 상세 판본 카드·판본 행에서는 FREE 도 표시) */
export function CopyrightBadge({ koreaCopyright }) {
  const badge = BADGE[koreaCopyright] ?? BADGE.UNKNOWN;
  return (
    <span className={`status-badge ${badge.className}`}>
      <span className="material-icons" aria-hidden="true">
        {badge.icon}
      </span>
      {formatCopyright(koreaCopyright)}
    </span>
  );
}
