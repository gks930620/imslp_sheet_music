import { Link } from "react-router-dom";
import { SearchBar } from "../sheetmusic/SearchBar.jsx";

/** 00_공통 §2-4 찾을 수 없는 페이지 (없는 곡·작곡가·작업 주소도 이 화면) */
export function NotFoundView() {
  return (
    <section className="empty-state not-found-view">
      <span className="material-icons">search_off</span>
      <h3>찾을 수 없는 페이지예요</h3>
      <p>주소가 틀렸거나 내려간 곡이에요</p>
      <div className="not-found-search">
        <SearchBar variant="large" />
      </div>
      <Link className="btn btn-primary" to="/">
        홈으로
      </Link>
    </section>
  );
}
