import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useDebouncedSearchInput } from "../../hooks/useDebouncedSearchInput.js";
import { callApi } from "../../lib/http.js";
import { applyAdminFilter } from "../../lib/adminQuery.js";
import { buildWorkQuery, withPage } from "../../lib/workQuery.js";
import { formatLifeSpan } from "../../lib/format.js";

const QUERY_KEYS = ["q", "missingKo", "page"];
const SKELETON_ROWS = [0, 1, 2, 3, 4, 5, 6, 7];

/** 05_관리자 화면 B — /admin/composers?q=&missingKo=&page= (02_API §4-2) */
export function ComposerAdminListPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const missingKo = searchParams.get("missingKo") === "true";
  const query = buildWorkQuery(searchParams, QUERY_KEYS);

  const list = useApiResource(() => callApi(`/api/admin/composers?${query}`).then((result) => result.data), {
    deps: [query],
  });

  const [text, changeText] = useDebouncedSearchInput(q, (next) =>
    setSearchParams(applyAdminFilter(searchParams, { q: next.trim() })),
  );

  const page = list.data;
  const composers = page?.content ?? [];
  const isEmpty = Boolean(page) && composers.length === 0;
  const isEmptyAll = isEmpty && !q;

  return (
    <div className="admin-composer-list">
      <nav className="breadcrumb">
        <Link to="/admin">관리</Link>
        <span> › </span>
        <span>작곡가 관리</span>
      </nav>

      <div className="page-header">
        <h1>
          <span className="material-icons">people</span>
          작곡가 관리
        </h1>
        {isEmptyAll ? null : (
          <Link className="btn btn-primary" to="/admin/composers/new">
            <span className="material-icons" aria-hidden="true">
              add
            </span>
            새 작곡가
          </Link>
        )}
      </div>

      <div className="admin-list-filters">
        <span className="search-bar search-bar-compact admin-list-search">
          <span className="search-bar-field">
            <span className="material-icons search-bar-icon" aria-hidden="true">
              search
            </span>
            <input
              className="search-bar-input"
              type="search"
              value={text}
              placeholder="이름으로 찾기"
              aria-label="이름으로 찾기"
              onChange={(event) => changeText(event.target.value)}
            />
          </span>
        </span>
        <label className="admin-list-checkbox">
          <input
            type="checkbox"
            checked={missingKo}
            onChange={(event) => setSearchParams(applyAdminFilter(searchParams, { missingKo: event.target.checked }))}
          />
          <span>한글 표기 없는 작곡가만</span>
        </label>
      </div>

      {list.error ? (
        <ErrorState onRetry={list.reload} />
      ) : !page ? (
        <div className="skeleton-list" aria-hidden="true">
          {SKELETON_ROWS.map((index) => (
            <div key={index} className="skeleton-row" />
          ))}
        </div>
      ) : isEmptyAll ? (
        <EmptyState icon="people_outline" title="등록된 작곡가가 없어요">
          <Link className="btn btn-primary" to="/admin/composers/new">
            <span className="material-icons" aria-hidden="true">
              add
            </span>
            새 작곡가
          </Link>
        </EmptyState>
      ) : isEmpty ? (
        <EmptyState icon="search_off" title={`'${q}'에 맞는 작곡가가 없어요`}>
          <button
            className="btn btn-text"
            type="button"
            onClick={() => setSearchParams(applyAdminFilter(searchParams, { q: "" }))}
          >
            검색 지우기
          </button>
        </EmptyState>
      ) : (
        <>
          <div className="data-table admin-composer-table">
            <div className="data-table-head">
              <span>한글 표기</span>
              <span>원어 표기</span>
              <span>생몰년</span>
              <span>곡 수</span>
              <span />
            </div>
            {composers.map((composer) => (
              <div
                key={composer.id}
                className="data-table-row admin-row"
                data-testid={`admin-composer-row-${composer.id}`}
                onClick={() => navigate(`/admin/composers/${composer.id}`)}
              >
                <span className={composer.nameKo ? "admin-composer-name" : "admin-composer-name-missing"}>
                  {composer.nameKo || "(없음)"}
                </span>
                <span className="admin-composer-original">{composer.nameOriginal}</span>
                <span className="admin-composer-life">{formatLifeSpan(composer.birthYear, composer.deathYear)}</span>
                <span className="admin-composer-works">{composer.workCount}</span>
                <Link className="btn btn-text" to={`/admin/composers/${composer.id}`}>
                  수정
                </Link>
              </div>
            ))}
          </div>
          <Pagination
            page={page.page}
            totalPages={page.totalPages}
            onChange={(next) => setSearchParams(withPage(searchParams, next))}
          />
        </>
      )}
    </div>
  );
}
