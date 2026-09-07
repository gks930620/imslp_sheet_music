import { useParams, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { FilterBar } from "../../components/sheetmusic/FilterBar.jsx";
import { WorkCard } from "../../components/sheetmusic/WorkCard.jsx";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { NotFoundView } from "../../components/common/NotFoundView.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { callPublicApi } from "../../lib/http.js";
import { formatLifeSpan } from "../../lib/format.js";
import { applyWorkFilters, buildWorkQuery, readWorkFilters, withPage } from "../../lib/workQuery.js";

/** 04_작곡가.md 화면 B — /composers/:id?sort=&level=&pages=&downloadable=&page= */
export function ComposerDetailPage() {
  const { id } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readWorkFilters(searchParams);
  const query = buildWorkQuery(searchParams, ["sort", "level", "pages", "downloadable", "page"]);

  const info = useApiResource(() => callPublicApi(`/api/composers/${id}`).then((result) => result.data), {
    deps: [id],
  });
  const worksResource = useApiResource(
    () => callPublicApi(`/api/composers/${id}/works?${query}`).then((result) => result.data),
    { deps: [id, query] },
  );

  if (info.error) {
    return info.error.status === 404 ? <NotFoundView /> : <ErrorState onRetry={info.reload} />;
  }

  const composer = info.data;
  const data = worksResource.data;
  const works = data?.works;
  const isEmpty = Boolean(data) && (works?.content?.length ?? 0) === 0;
  const isOutOfRange = isEmpty && (works?.totalElements ?? 0) > 0;

  const aliasLine = composer?.aliases?.length ? `${composer.aliases.join(", ")}이라고도 씁니다` : "";
  const factLine = composer
    ? [formatLifeSpan(composer.birthYear, composer.deathYear), composer.nationality].filter(Boolean).join(" · ")
    : "";

  return (
    <div className="composer-detail">
      {composer ? (
        <section className="composer-detail-info">
          <h1 className="composer-detail-name">{composer.nameKo || composer.nameOriginal}</h1>
          {composer.nameKo && composer.nameOriginal ? (
            <p className="composer-detail-original">{composer.nameOriginal}</p>
          ) : null}
          {factLine ? <p className="composer-detail-facts">{factLine}</p> : null}
          {aliasLine ? <p className="composer-detail-aliases">{aliasLine}</p> : null}
          {composer.imslpUrl ? (
            <p className="composer-detail-imslp">
              <a href={composer.imslpUrl} target="_blank" rel="noreferrer">
                IMSLP 작곡가 페이지 보기
                <span className="material-icons" aria-hidden="true">
                  open_in_new
                </span>
              </a>
            </p>
          ) : null}
        </section>
      ) : (
        <div className="skeleton-detail" aria-hidden="true">
          <div className="skeleton-row" />
          <div className="skeleton-row" />
        </div>
      )}

      {worksResource.error ? (
        <ErrorState onRetry={worksResource.reload} />
      ) : (
        <section className="composer-detail-works">
          <div className="composer-detail-works-head">
            <p className="composer-detail-count">
              {data ? `곡 ${data.unfilteredTotal}개${filters.hasFilter ? ` 중 ${works?.totalElements ?? 0}개` : ""}` : ""}
            </p>
            <select
              className="filter-select"
              aria-label="정렬"
              value={searchParams.get("sort") ?? "downloads"}
              onChange={(event) => {
                const next = new URLSearchParams(searchParams);
                next.set("sort", event.target.value);
                next.delete("page");
                setSearchParams(next);
              }}
            >
              <option value="downloads">다운로드 많은 순</option>
              <option value="opus">작품번호 순</option>
            </select>
          </div>

          <FilterBar
            levels={filters.levels}
            pages={filters.pages}
            downloadable={filters.downloadable}
            onChange={(next) => setSearchParams(applyWorkFilters(searchParams, next))}
          />

          {!data ? (
            <div className="skeleton-list" aria-hidden="true">
              {[0, 1, 2, 3, 4].map((index) => (
                <div key={index} className="skeleton-row" />
              ))}
            </div>
          ) : isOutOfRange ? (
            <EmptyState icon="search_off" title="이 페이지에는 곡이 없어요">
              <button className="btn btn-text" type="button" onClick={() => setSearchParams(withPage(searchParams, 0))}>
                첫 페이지로
              </button>
            </EmptyState>
          ) : isEmpty ? (
            filters.hasFilter ? (
              <EmptyState icon="filter_alt_off" title="이 조건에 맞는 곡이 없어요" />
            ) : (
              <EmptyState icon="library_music" title="아직 공개된 곡이 없어요" />
            )
          ) : (
            <>
              <div className="post-list">
                {works.content.map((work) => (
                  <WorkCard key={work.id} work={work} hideComposer hideAlias />
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
        </section>
      )}
    </div>
  );
}
