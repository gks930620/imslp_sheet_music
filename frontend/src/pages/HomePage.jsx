import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { SearchBar } from "../components/sheetmusic/SearchBar.jsx";
import { SearchScopeSelect } from "../components/sheetmusic/SearchScopeSelect.jsx";
import { WorkCard } from "../components/sheetmusic/WorkCard.jsx";
import { ErrorState } from "../components/common/ErrorState.jsx";
import { useApiResource } from "../hooks/useApiResource.js";
import { useSection, useSectionPath } from "../hooks/useSection.js";
import { useDocumentTitle } from "../hooks/useDocumentTitle.js";
import { callPublicApi } from "../lib/http.js";
import { DEFAULT_SCOPE, SCOPE_EXAMPLES, buildSearchHref } from "../lib/searchScope.js";
import { linkSection } from "../lib/sections.js";
import { clearRecentWorks, readRecentWorkIds } from "../lib/recentWorks.js";

/**
 * 01_홈.md — 검색창 하나가 주인공. 인기곡·작곡가는 독립 요청이라 각각 실패 처리한다.
 * 검색 기준은 화면이 잠시 드는 상태다(기획 04 §4-5 — 저장하지 않는다, 다시 들어오면 항상 "전체").
 * 고르기만 해서는 검색이 일어나지 않는다 — 자리 문구와 예시 칩만 바뀐다(08 §4-4).
 */
export function HomePage() {
  const navigate = useNavigate();
  const section = linkSection(useSection());
  const sectionPath = useSectionPath();
  const [scope, setScope] = useState(DEFAULT_SCOPE);
  useDocumentTitle(`쉬운악보 — ${section.label}`);

  // 기획 04 §5 · 02 §7 — 인기곡·작곡가는 현재 구분의 것만
  const popular = useApiResource(
    () => callPublicApi(`/api/works/popular?section=${section.code}&limit=10`).then((r) => r.data ?? []),
    { deps: [section.code] },
  );
  const composers = useApiResource(
    () => callPublicApi(`/api/composers/featured?section=${section.code}&limit=8`).then((r) => r.data ?? []),
    { deps: [section.code] },
  );

  // 기획 §11-3-6 — 인기곡은 순위표가 아니라 견본 진열대다. 보여줄 곡이 없으면 자리를 남기지 않는다
  // (불러오는 중·실패는 자리를 지킨다 — 비어 있는 것과 못 불러온 것은 다른 사실이다)
  const showPopular = Boolean(popular.loading || popular.error || popular.data?.length);

  // 01_홈(2026-09-20) · 03 §23 — 저장된 곡이 **1개 이상일 때만** 영역이 생긴다.
  // 없으면 요청도 보내지 않는다: 첫 방문·시크릿 창의 홈은 2026-09-10 시안과 한 글자도 다르지 않다(8-E 2·10).
  // ⚠ 이 초기값은 **첫 마운트에서 한 번만** 읽는다 — 구분을 바꿔도 다시 읽지 않는다.
  // 지금은 구분마다 라우트가 따로 있어(App.jsx — `section.slug` 별 Route) 전환 시 이 페이지가 remount 되므로 안전하다.
  // 그 전제가 깨지면(한 Route 안에서 구분만 바뀌게 되면) 앞 구분의 최근 본 곡이 남는다 — 그때는 section.code 를 읽는 effect/key 로 바꾸어야 한다.
  const [recentIds, setRecentIds] = useState(() => readRecentWorkIds(section.code));
  const recentQuery = recentIds.join(",");
  const recent = useApiResource(
    () => callPublicApi(`/api/works/recent?ids=${recentQuery}&section=${section.code}`).then((r) => r.data ?? []),
    { deps: [recentQuery, section.code], enabled: recentIds.length > 0 },
  );
  // 보여줄 것이 없으면(실패·전부 숨김) 영역째 사라진다 — 오류 상자·다시 시도 없음(8-E 11)
  const showRecent = recentIds.length > 0 && !recent.error && (recent.loading || Boolean(recent.data?.length));

  return (
    <div className="home">
      <section className="home-intro">
        <h1 className="home-title">쉬운악보</h1>
        <p className="home-subtitle">한국어로 검색하고 바로 받는 피아노 악보</p>
        <div className="home-search">
          {/* 08 §4-2 — 세그먼트는 검색창 바로 위, 왼쪽 끝을 검색창에 맞춘다 (가운데 정렬 아님) */}
          <div className="search-scope">
            <SearchScopeSelect value={scope} onChange={setScope} />
          </div>
          <SearchBar variant="large" autoFocus scope={scope} />
        </div>
        <div className="home-examples">
          <span className="home-examples-label">예:</span>
          {/* 08 §4-5 D9 — 칩은 기준을 따라 바뀌고, 누르면 현재 기준 그대로 검색한다(칩이 기준을 바꾸지 않는다) */}
          {SCOPE_EXAMPLES[scope].map((example) => (
            <button
              key={example}
              className="example-chip"
              type="button"
              onClick={() => navigate(buildSearchHref(sectionPath("/search"), { q: example, scope }))}
            >
              {example}
            </button>
          ))}
        </div>
      </section>

      {/* 01_홈 D11 — 최근 본 곡이 있으면 데스크톱에서 인기곡과 **같은 7fr 열**에 세로로 서고 작곡가 열은 제자리에 남는다 */}
      <div className={showRecent ? "home-sections has-recent" : "home-sections"}>
        {showRecent ? (
          <section className="home-recent">
            <div className="section-title-row">
              <h2 className="section-title">최근 본 곡</h2>
              {/* 확인 창·되돌리기 없음 — 공용 컴퓨터에서 한 번에 없애는 수단이다(기획 05 §4-2) */}
              <button
                className="btn btn-text"
                type="button"
                aria-label="최근 본 곡 지우기"
                onClick={() => {
                  clearRecentWorks(section.code);
                  setRecentIds([]);
                }}
              >
                지우기
              </button>
            </div>
            {recent.data?.length ? (
              <div className="post-list">
                {recent.data.map((work) => (
                  <WorkCard key={work.id} work={work} variant="compact" />
                ))}
              </div>
            ) : (
              // 저장된 곡 수만큼 먼저 자리를 잡는다 — 응답 후에 끼워 넣으면 인기곡이 한 화면 아래로 튄다
              <div className="skeleton-list" aria-hidden="true">
                {recentIds.map((id) => (
                  <div key={id} className="skeleton-row" />
                ))}
              </div>
            )}
          </section>
        ) : null}

        {showPopular ? (
          <section className="home-popular">
            <h2 className="section-title">지금 바로 받을 수 있는 인기곡</h2>
            {popular.error ? (
              <ErrorState compact onRetry={popular.reload} />
            ) : popular.loading ? (
              <div className="skeleton-list" aria-hidden="true">
                {[0, 1, 2, 3, 4].map((index) => (
                  <div key={index} className="skeleton-row" />
                ))}
              </div>
            ) : (
              <div className="post-list">
                {popular.data.map((work, index) => (
                  <WorkCard key={work.id} work={work} variant="compact" rank={index + 1} />
                ))}
              </div>
            )}
          </section>
        ) : null}

        <section className="home-composers">
          <div className="section-title-row">
            <h2 className="section-title">작곡가</h2>
            <Link className="btn btn-text" to={sectionPath("/composers")}>
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
                <Link key={composer.id} className="composer-card" to={sectionPath(`/composers/${composer.id}`)}>
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
