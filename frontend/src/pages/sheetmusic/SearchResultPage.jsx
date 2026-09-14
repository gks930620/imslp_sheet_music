import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { SearchBar } from "../../components/sheetmusic/SearchBar.jsx";
import { SearchScopeSelect } from "../../components/sheetmusic/SearchScopeSelect.jsx";
import { FilterBar } from "../../components/sheetmusic/FilterBar.jsx";
import { WorkCard } from "../../components/sheetmusic/WorkCard.jsx";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useSection, useSectionPath } from "../../hooks/useSection.js";
import { useDocumentTitle } from "../../hooks/useDocumentTitle.js";
import { callPublicApi } from "../../lib/http.js";
import { imslpSearchUrl } from "../../lib/imslp.js";
import { DEFAULT_SCOPE, SCOPE_FIELD_LABEL, readScopeParam, withScope } from "../../lib/searchScope.js";
import { findSectionBySlug, linkSection } from "../../lib/sections.js";
import { applyWorkFilters, buildWorkQuery, readWorkFilters, withPage } from "../../lib/workQuery.js";

const MAX_COMPOSER_CARDS = 3;
/** 02 §7 — 주소 쿼리 중 API 로 넘기는 것. 화면 전용 `from` 은 없다 */
const SEARCH_KEYS = ["q", "in", "level", "pages", "downloadable", "page"];
/**
 * 08 §2-4 — 준비 중 구분의 헤더 검색으로 왔을 때의 한 줄. 조사는 구분마다 고정 문자열(계산하지 않는다).
 * 알 수 없는 값(`from=cello`, `from=piano`)은 줄을 만들지 않는다 — 오류 아님.
 */
const FROM_NOTICE = {
  violin: "바이올린은 준비 중이라 피아노 악보에서 찾았어요.",
  orchestra: "오케스트라는 준비 중이라 피아노 악보에서 찾았어요.",
};

/** 08 §4-7 D8 — 0건 화면은 셋 중 하나만. 세 블록이 동시에 렌더될 수 있는 구조를 만들지 않는다 */
const EMPTY = { FILTER: "FILTER", SCOPE: "SCOPE", NONE: "NONE" };

function resolveEmptyKind({ data, hasFilter, scope }) {
  const works = data?.works;
  const isEmpty = Boolean(data) && (works?.content?.length ?? 0) === 0;
  if (!isEmpty) return null;
  if ((works?.totalElements ?? 0) > 0) return "OUT_OF_RANGE";
  // [A] 필터가 선택돼 있으면 무조건 필터 안내 — 풀면 결과가 있는지 화면이 알 필요 없다(가까운 원인부터)
  if (hasFilter) return EMPTY.FILTER;
  // [B] 는 "전체 기준에 결과가 있다고 확인됐을 때"만 — totalInAll 은 숫자/0/null 셋이 다르다.
  //     null(모름)을 0 과 같게 취급하지 않고, 추측으로 출구를 띄우지 않는다 (02 §3-1)
  const totalInAll = data.totalInAll;
  if (scope !== DEFAULT_SCOPE && totalInAll != null && totalInAll > 0) return EMPTY.SCOPE;
  return EMPTY.NONE;
}

/** 02_검색결과.md — URL 쿼리(q, in, level, pages, downloadable, page + 화면 전용 from)가 상태의 원본 */
export function SearchResultPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const section = linkSection(useSection());
  const sectionPath = useSectionPath();
  const q = searchParams.get("q") ?? "";
  const hasQuery = Boolean(q.trim());
  const filters = readWorkFilters(searchParams);
  const urlScope = readScopeParam(searchParams.get("in"));
  // 검색어가 없을 때 고른 기준 — 이동할 곳이 없으므로 화면이 잠시 든다(인수 조건 8-E 2). 검색어가 있으면 주소가 근원
  const [idleScope, setIdleScope] = useState(urlScope);
  useDocumentTitle(hasQuery ? `'${q.trim()}' 검색 결과 — 쉬운악보 ${section.label}` : `검색 — 쉬운악보 ${section.label}`);

  // 02 §7 — 주소 쿼리를 그대로 전달하되 in=ALL 은 생략하고, from 은 보내지 않으며, 구분은 경로에서 읽어 싣는다
  const apiParams = new URLSearchParams(buildWorkQuery(searchParams, SEARCH_KEYS));
  if (urlScope === DEFAULT_SCOPE) apiParams.delete("in");
  else apiParams.set("in", urlScope);
  apiParams.set("section", section.code);
  const query = apiParams.toString();

  const search = useApiResource(() => callPublicApi(`/api/works/search?${query}`).then((r) => r.data), {
    deps: [query],
    enabled: hasQuery,
  });

  const data = search.data;
  const works = data?.works;
  // 세그먼트가 보여 주는 기준 — 주소의 in(서버와 같은 관용 규칙으로 읽는다). 응답의 in 은 관용 처리가 끝난 같은 값이라
  // (02 §3-1) 주소 하나만 근원으로 둔다(03 §21-6). 검색어가 없을 때만 화면이 잠시 든 값을 쓴다
  const scope = hasQuery ? urlScope : idleScope;
  const emptyKind = resolveEmptyKind({ data, hasFilter: filters.hasFilter, scope });

  const popular = useApiResource(
    () => callPublicApi(`/api/works/popular?section=${section.code}&limit=5`).then((r) => r.data ?? []),
    { deps: [emptyKind === EMPTY.NONE, section.code], enabled: emptyKind === EMPTY.NONE },
  );

  // 이 화면의 모든 주소 갱신은 push 이고, 화면 전용 from 을 이어 붙이지 않는다 — 안내 줄이 이 검색 한 번에만 보인다(08 §2-4)
  const push = (next) => {
    next.delete("from");
    setSearchParams(next);
  };

  // 기획 04 §4-3 — 검색어·필터 유지, 페이지만 1페이지로. 검색창이 비어 있으면 선택 표시만 바뀐다(인수 조건 8-E 2)
  const changeScope = (next) => {
    if (!hasQuery) {
      setIdleScope(next);
      return;
    }
    const params = withScope(searchParams, next);
    params.delete("page");
    push(params);
  };

  const fromNotice = FROM_NOTICE[findSectionBySlug(searchParams.get("from"))?.slug] ?? null;
  const shownQ = data?.q ?? q;
  const fieldLabel = SCOPE_FIELD_LABEL[scope];
  const showFilterBar = Boolean(data) && emptyKind !== EMPTY.SCOPE && emptyKind !== EMPTY.NONE;
  // 곡명 기준은 작곡가를 찾지 않기로 한 약속이다 — 서버가 카드를 실어 보내도 그리지 않는다(화면정의 02 요소표, 인수 조건 8-D 5)
  const composers = scope === "TITLE" ? [] : (data?.composers ?? []);
  const moreComposers = scope === "TITLE" ? 0 : (data?.composerMatchCount ?? 0) - composers.length;
  // 기획 §11-4 · 화면정의 02 "[A] 안에서 유일하게 허용되는 한 줄" — 카드가 정확히 1명일 때만 딸린 출구
  const exitComposer = composers.length === 1 && composers[0].workCount > 0 ? composers[0] : null;

  return (
    <div className="search-result">
      <div className="search-result-search">
        <div className="search-scope">
          <SearchScopeSelect value={scope} onChange={changeScope} />
        </div>
        <SearchBar variant="large" initialValue={q} autoFocus={!hasQuery} scope={scope} />
      </div>

      {!hasQuery ? (
        <EmptyState icon="search" title="찾고 싶은 곡 이름, 작곡가, 작품번호를 입력해 주세요" />
      ) : search.error ? (
        <ErrorState onRetry={search.reload} />
      ) : (
        <>
          {fromNotice ? (
            <InlineAlert variant="info" className="search-from-notice">
              <p>{fromNotice}</p>
            </InlineAlert>
          ) : null}

          {composers.length ? (
            <div className="composer-match-list">
              {composers.slice(0, MAX_COMPOSER_CARDS).map((composer) => (
                <Link
                  key={composer.id}
                  className="composer-match-card"
                  to={sectionPath(`/composers/${composer.id}`)}
                >
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
                <Link className="btn btn-text composer-match-more" to={sectionPath("/composers")}>
                  {`작곡가 ${moreComposers}명 더 — 작곡가 목록에서 찾기`}
                </Link>
              ) : null}
            </div>
          ) : null}

          {search.loading && !data ? (
            <div className="skeleton-row search-count-skeleton" aria-hidden="true" />
          ) : data ? (
            <p className="search-count">
              <span className="search-count-q">{`'${shownQ}'`}</span>{" "}
              {`검색 결과 ${data.unfilteredTotal}곡`}
              {filters.hasFilter ? ` 중 ${works?.totalElements ?? 0}곡` : ""}
              {/* 화면정의 02 요소표 — 기준이 전체가 아니면 "왜 적은가"를 그 자리에서 답한다. 사실 고지이지 권유가 아니다 */}
              {fieldLabel ? <span className="search-count-scope">{` · ${fieldLabel}에서 찾음`}</span> : null}
            </p>
          ) : null}

          {showFilterBar ? (
            <FilterBar
              levels={filters.levels}
              pages={filters.pages}
              downloadable={filters.downloadable}
              onChange={(next) => push(applyWorkFilters(searchParams, next))}
            />
          ) : null}

          {search.loading ? (
            // 08 §4-4 — 기준·필터를 바꾸면 목록 자리만 스켈레톤. 검색창·세그먼트·필터 바는 다시 그리지 않는다
            <div className="skeleton-list" aria-hidden="true">
              {[0, 1, 2, 3, 4].map((index) => (
                <div key={index} className="skeleton-row" />
              ))}
            </div>
          ) : !data ? null : emptyKind === "OUT_OF_RANGE" ? (
            <EmptyState icon="search_off" title="이 페이지에는 곡이 없어요">
              <button className="btn btn-text" type="button" onClick={() => push(withPage(searchParams, 0))}>
                첫 페이지로
              </button>
            </EmptyState>
          ) : emptyKind === EMPTY.FILTER ? (
            /* [A] 필터 때문에 0건 — 기준 이야기·기존 3줄·인기곡·작곡가 목록 링크는 하나도 나오지 않는다.
               "필터 해제" 는 위에 남아 있는 필터 바(FilterBar)가 이미 갖고 있다 */
            <EmptyState icon="filter_alt_off" title="이 조건에 맞는 곡이 없어요">
              {exitComposer ? (
                <Link className="btn btn-text" to={sectionPath(`/composers/${exitComposer.id}`)}>
                  {`${exitComposer.nameKo || exitComposer.nameOriginal}의 곡은 ${exitComposer.workCount}개 등록돼 있어요 — 조건 없이 모두 보기`}
                  <span className="material-icons" aria-hidden="true">
                    arrow_forward
                  </span>
                </Link>
              ) : null}
            </EmptyState>
          ) : emptyKind === EMPTY.SCOPE ? (
            /* [B] 기준이 전체가 아니고 전체 기준에 결과가 있다 — 확실한 출구 하나만, 조언 더미에 묻지 않는다 */
            <EmptyState
              icon="search_off"
              title={
                <>
                  <span className="search-count-q">{`'${shownQ}'`}</span>
                  {`을 ${fieldLabel}에서 찾지 못했어요`}
                </>
              }
              description={`전체에서 찾으면 ${data.totalInAll}곡이 있어요`}
            >
              <button className="btn btn-primary" type="button" onClick={() => changeScope(DEFAULT_SCOPE)}>
                전체로 찾기
                <span className="material-icons" aria-hidden="true">
                  arrow_forward
                </span>
              </button>
            </EmptyState>
          ) : emptyKind === EMPTY.NONE ? (
            /* [C] 그 외 — 기존 0건 안내. 좁히는 방향의 안내는 어느 경우에도 만들지 않는다(기획 04 §4-4) */
            <div className="search-no-match">
              <EmptyState icon="search_off" title={`'${shownQ}'에 맞는 곡을 찾지 못했어요`}>
                {fieldLabel ? (
                  // 08 §4-7 — 사실 고지이지 출구가 아니다. 버튼이 아니고 "전체로 찾으면 있다"고 약속하지 않는다
                  <p className="search-no-match-scope">{`지금은 '${fieldLabel}'에서만 찾고 있어요`}</p>
                ) : null}
                <p>다른 이름으로 불리기도 해요 — 예: 월광 / Moonlight / Op.27 No.2</p>
                <p>작곡가 이름으로 찾아보세요</p>
                <p>지금은 피아노 악보만 있어요 (바이올린·오케스트라는 준비 중)</p>
                {/* 기획 §10-7 — 0건이 "없어요" 가 아니라 "우리가 일부러 뺐어요" 인 경우가 많다. 원본으로 안내한다 */}
                {imslpSearchUrl(shownQ) ? (
                  <a className="btn btn-outline" href={imslpSearchUrl(shownQ)} target="_blank" rel="noreferrer">
                    IMSLP 에서 직접 찾아보기
                    <span className="material-icons" aria-hidden="true">
                      open_in_new
                    </span>
                  </a>
                ) : null}
                <Link className="btn btn-outline" to={sectionPath("/composers")}>
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
                  push(withPage(searchParams, page));
                  window.scrollTo(0, 0);
                }}
              />
            </>
          )}
        </>
      )}
    </div>
  );
}
