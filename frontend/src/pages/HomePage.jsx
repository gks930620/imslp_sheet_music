import { Link, useNavigate } from "react-router-dom";
import { SearchBar } from "../components/sheetmusic/SearchBar.jsx";
import { WorkCard } from "../components/sheetmusic/WorkCard.jsx";
import { ErrorState } from "../components/common/ErrorState.jsx";
import { useApiResource } from "../hooks/useApiResource.js";
import { callPublicApi } from "../lib/http.js";

const EXAMPLES = ["월광", "쇼팽 녹턴", "K.545"];

/** 01_홈.md — 검색창 하나가 주인공. 인기곡·작곡가는 독립 요청이라 각각 실패 처리한다. */
export function HomePage() {
  const navigate = useNavigate();

  const popular = useApiResource(() => callPublicApi("/api/works/popular?limit=10").then((r) => r.data ?? []), {
    deps: [],
  });
  const composers = useApiResource(() => callPublicApi("/api/composers/featured?limit=8").then((r) => r.data ?? []), {
    deps: [],
  });

  return (
    <div className="home">
      <section className="home-intro">
        <h1 className="home-title">쉬운악보</h1>
        <p className="home-subtitle">한국어로 검색하고 바로 받는 피아노 악보</p>
        <div className="home-search">
          <SearchBar variant="large" autoFocus />
        </div>
        <div className="home-examples">
          <span className="home-examples-label">예:</span>
          {EXAMPLES.map((example) => (
            <button
              key={example}
              className="example-chip"
              type="button"
              onClick={() => navigate(`/search?q=${encodeURIComponent(example)}`)}
            >
              {example}
            </button>
          ))}
        </div>
      </section>

      <div className="home-sections">
        <section className="home-popular">
          <h2 className="section-title">인기곡</h2>
          {popular.error ? (
            <ErrorState compact onRetry={popular.reload} />
          ) : popular.loading ? (
            <div className="skeleton-list" aria-hidden="true">
              {[0, 1, 2, 3, 4].map((index) => (
                <div key={index} className="skeleton-row" />
              ))}
            </div>
          ) : popular.data?.length ? (
            <div className="post-list">
              {popular.data.map((work, index) => (
                <WorkCard key={work.id} work={work} variant="compact" rank={index + 1} />
              ))}
            </div>
          ) : (
            <div className="home-empty">
              <span className="material-icons">library_music</span>
              <p>아직 등록된 곡이 없어요</p>
            </div>
          )}
        </section>

        <section className="home-composers">
          <div className="section-title-row">
            <h2 className="section-title">작곡가</h2>
            <Link className="btn btn-text" to="/composers">
              모든 작곡가 보기
              <span className="material-icons" aria-hidden="true">
                chevron_right
              </span>
            </Link>
          </div>
          {composers.error ? (
            <ErrorState compact onRetry={composers.reload} />
          ) : composers.loading ? (
            <div className="composer-grid" aria-hidden="true">
              {[0, 1, 2, 3].map((index) => (
                <div key={index} className="skeleton-card" />
              ))}
            </div>
          ) : composers.data?.length ? (
            <div className="composer-grid">
              {composers.data.map((composer) => (
                <Link key={composer.id} className="composer-card" to={`/composers/${composer.id}`}>
                  <span className="composer-card-name">{composer.nameKo || composer.nameOriginal}</span>
                  <span className="composer-card-count">{composer.workCount}곡</span>
                </Link>
              ))}
            </div>
          ) : (
            <div className="home-empty">
              <p>아직 등록된 작곡가가 없어요</p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
