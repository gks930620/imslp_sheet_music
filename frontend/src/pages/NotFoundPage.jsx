import { NotFoundView } from "../components/common/NotFoundView.jsx";

/** 00_공통 §2-4 — 찾을 수 없는 페이지. 없는 구분 이름(`/cello`)·준비 중 구분의 하위 경로(`/violin/search`)도 여기로 온다(08 §3) */
export function NotFoundPage() {
  return <NotFoundView />;
}
