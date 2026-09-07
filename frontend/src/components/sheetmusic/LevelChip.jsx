import { formatLevel } from "../../lib/format.js";

const CLASS_BY_LEVEL = {
  BEGINNER: "level-chip-beginner",
  ELEMENTARY: "level-chip-elementary",
  INTERMEDIATE: "level-chip-intermediate",
  ADVANCED: "level-chip-advanced",
};

/** 00_공통 §3-3 난이도 칩 (pill, 아이콘 없음) */
export function LevelChip({ level }) {
  return <span className={`level-chip ${CLASS_BY_LEVEL[level] ?? "level-chip-unknown"}`}>{formatLevel(level)}</span>;
}
