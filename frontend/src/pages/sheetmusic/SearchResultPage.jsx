import { Link, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { SearchBar } from "../../components/sheetmusic/SearchBar.jsx";
import { FilterBar } from "../../components/sheetmusic/FilterBar.jsx";
import { WorkCard } from "../../components/sheetmusic/WorkCard.jsx";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { callPublicApi } from "../../lib/http.js";
import { imslpSearchUrl } from "../../lib/imslp.js";
import { applyWorkFilters, buildWorkQuery, readWorkFilters, withPage } from "../../lib/workQuery.js";

const MAX_COMPOSER_CARDS = 3;

/** 02_검색결과.md — URL 쿼리(q, level, pages, downloadable, page)가 상태의 원본 */
export function SearchResultPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const filters = readWorkFilters(searchParams);
  const query = buildWorkQuery(searchParams, ["q", "level", "pages", "downloadable", "page"]);

  const search = useApiResource(() => callPublicApi(`/api/works/search?${query}`).then((r) => r.data), {
    deps: [query],
    enabled: Boolean(q.trim()),
  });

  const data = search.data;
  const works = data?.works;
  const isEmptyResult = Boolean(data) && (works?.content?.length ?? 0) === 0;
  const isOutOfRange = isEmptyResult && (works?.totalElements ?? 0) > 0;
  const isNoMatch = isEmptyResult && !isOutOfRange && !filters.hasFilter;

  const popular = useApiResource(() => callPublicApi("/api/works/popular?limit=5").then((r) => r.data ?? []), {
    deps: [isNoMatch],
    enabled: isNoMatch,
  });

  const showFilterBar = Boolean(data) && !isNoMatch;
  const composers = data?.composers ?? [];
  const moreComposers = (data?.composerMatchCount ?? 0) - composers.length;
  // 기획 §11-4 — 필터로 0건이 된 화면에서 작곡가 카드는 결과가 아니라 출구다. 그 뜻을 한 줄로 잇는다
  const exitComposer = composers.find((composer) => composer.workCount > 0) ?? null;

  return (
    <div className="search-result">
      <div className="search-result-search">
        <SearchBar variant="large" initialValue={q} autoFocus={!q.trim()} />
      </div>

      {!q.trim() ? (
        <EmptyState icon="search" title="찾고 싶은 곡 이름, 작곡가, 작품번호를 입력해 주세요" />
      ) : search.error ? (
        <ErrorState onRetry={search.reload} />
      ) : (
        <>
          {composers.length ? (
            <div className="composer-match-list">
              {composers.slice(0, MAX_COMPOSER_CARDS).map((composer) => (
                <Link key={composer.id} className="composer-match-card" to={`/composers/${composer.id}`}>
                  <span className="material-icons">person</span>
                  <span className="composer-match-body">
                    <span className="composer-match-name">
                      {`작곡가: ${composer.nameKo || composer.nameOriginal}${composer.nameKo ? ` (${composer.nameOriginal})` : ""}`}
                    </span>
                    {/* 기획 §11-4 — 이 숫자는 필터·페이지와 무관한 "작곡가 페이지에 가면 있는 곡 수"다 */}
                    <span className="composer-match-count">{`등록된 곡 ${composer.workCount}개 모두 보기`}</span>
                  </span>
                  <span className="material-icons" aria-hidden="true">
                    chevron_right
                  </span>
                </Link>
              ))}
              {moreComposers > 0 ? (
                <Link className="btn btn-text composer-match-more" to="/composers">
                  {`작곡가 ${moreComposers}명 더 — 작곡가 목록에서 찾기`}
                </Link>
              ) : null}
            </div>
          ) : null}

          {search.loading && !data ? (
            <div className="skeleton-list" aria-hidden="true">
              {[0, 1, 2, 3, 4].map((index) => (
                <div key={index} className="skeleton-row" />
              ))}
            </div>
          ) : null}

          {data ? (
            <>
              <p className="search-count">
                <span className="search-count-q">{`'${data.q ?? q}'`}</span>{" "}
                {`검색 결과 ${data.unfilteredTotal}곡`}
                {filters.hasFilter ? ` 중 ${works?.totalElements ?? 0}곡` : ""}
              </p>

              {showFilterBar ? (
                <FilterBar
                  levels={filters.levels}
                  pages={filters.pages}
                  downloadable={filters.downloadable}
                  onChange={(next) => setSearchParams(applyWorkFilters(searchParams, next))}
                />
              ) : null}

              {isOutOfRange ? (
                <EmptyState icon="search_off" title="이 페이지에는 곡이 없어요">
                  <button
                    className="btn btn-text"
                    type="button"
                    onClick={() => setSearchParams(withPage(searchParams, 0))}
                  >
                    첫 페이지로
                  </button>
                </EmptyState>
              ) : isNoMatch ? (
                <div className="search-no-match">
                  <EmptyState icon="search_off" title={`'${data.q ?? q}'에 맞는 곡을 찾지 못했어요`}>
                    <p>다른 이름으로 불리기도 해요 — 예: 월광 / Moonlight / Op.27 No.2</p>
                    <p>작곡가 이름으로 찾아보세요</p>
                    <p>1차는 피아노 독주곡만 있어요</p>
                    {/* 기획 §10-7 — 0건이 "없어요" 가 아니라 "우리가 일부러 뺐어요" 인 경우가 많다. 원본으로 안내한다 */}
                    {imslpSearchUrl(data.q ?? q) ? (
                      <a
                        className="btn btn-outline"
                        href={imslpSearchUrl(data.q ?? q)}
                        target="_blank"
                        rel="noreferrer"
                      >
                        IMSLP 에서 직접 찾아보기
                        <span className="material-icons" aria-hidden="true">
                          open_in_new
                        </span>
                      </a>
                    ) : null}
                    <Link className="btn btn-outline" to="/composers">
                      작곡가 목록 보기
                    </Link>
                  </EmptyState>
                  {popular.data?.length ? (
                    <section className="search-no-match-popular">
                      <h2 className="section-title">인기곡</h2>
                      <div className="post-list">
                        {popular.data.map((work, index) => (
                          <WorkCard key={work.id} work={work} variant="compact" rank={index + 1} />
                        ))}
                      </div>
                    </section>
                  ) : null}
                </div>
              ) : isEmptyResult ? (
                <EmptyState icon="filter_alt_off" title="이 조건에 맞는 곡이 없어요">
                  {exitComposer ? (
                    <Link className="btn btn-text" to={`/composers/${exitComposer.id}`}>
                      {`${exitComposer.nameKo || exitComposer.nameOriginal}의 곡은 ${exitComposer.workCount}개 등록돼 있어요 — 조건 없이 모두 보기`}
                    </Link>
                  ) : null}
                </EmptyState>
              ) : (
                <>
                  <div className="post-list">
                    {works.content.map((work) => (
                      <WorkCard key={work.id} work={work} />
                    ))}
                  </div>
                  <Pagination
                    page={works.page}
                    totalPages={works.totalPages}
                    onChange={(page) => {
                      setSearchParams(withPage(searchParams, page));
                      window.scrollTo(0, 0);
                    }}
                  />
                </>
              )}
            </>
          ) : null}
        </>
      )}
    </div>
  );
}
