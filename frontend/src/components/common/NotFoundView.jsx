import { Link } from "react-router-dom";
import { SearchBar } from "../sheetmusic/SearchBar.jsx";
import { useSectionPath } from "../../hooks/useSection.js";
import { useDocumentTitle } from "../../hooks/useDocumentTitle.js";

/** 00 §2-4 (2026-09-10 교체) — 없는 구분 이름 주소도 이 화면으로 오므로 보조 문구가 구분을 함께 말한다 */
const NOT_FOUND_DESCRIPTION = "주소가 틀렸거나, 내려간 곡이거나, 아직 열지 않은 악기 구분이에요";

/**
 * 00_공통 §2-4 찾을 수 없는 페이지 (없는 곡·작곡가·작업 주소·없는 구분 이름 주소 모두 이 화면).
 * 주소에 든 문자열은 화면에 되뱉지 않는다(08 §3). 검색·"홈으로"는 기본 구분(피아노)으로 간다.
 * 브라우저 탭 제목에는 구분 이름이 붙지 않는다(00 §4-1).
 */
export function NotFoundView({ description = NOT_FOUND_DESCRIPTION }) {
  const sectionPath = useSectionPath();
  useDocumentTitle("찾을 수 없는 페이지 — 쉬운악보");

  return (
    <section className="empty-state not-found-view">
      <span className="material-icons">search_off</span>
      <h3>찾을 수 없는 페이지예요</h3>
      <p>{description}</p>
      <div className="not-found-search">
        <SearchBar variant="large" />
      </div>
      <Link className="btn btn-primary" to={sectionPath()}>
        홈으로
      </Link>
    </section>
  );
}
