import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { LevelChip } from "../../components/sheetmusic/LevelChip.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useComposerOptions } from "../../hooks/useComposerOptions.js";
import { useDebouncedSearchInput } from "../../hooks/useDebouncedSearchInput.js";
import { callApi } from "../../lib/http.js";
import { applyAdminFilter, hasAdminFilter } from "../../lib/adminQuery.js";
import { buildWorkQuery, withPage } from "../../lib/workQuery.js";

const FILTER_KEYS = ["q", "status", "composerId", "level"];
const QUERY_KEYS = [...FILTER_KEYS, "page"];
const SKELETON_ROWS = [0, 1, 2, 3, 4, 5, 6, 7];

/** 05-D 상태 필터 (02_API §4-6) — 관리 화면 문구는 사용자 뱃지와 다르다 */
const STATUS_OPTIONS = [
  { value: "", label: "전체" },
  { value: "READY", label: "바로 받기 가능" },
  { value: "PREPARING", label: "준비 중" },
  { value: "RESTRICTED", label: "이용 제한" },
  { value: "UNKNOWN", label: "저작권 확인 중" },
  { value: "NEEDS_WORK", label: "보완 필요" },
  // WorkStatus 가 아닌 목록 필터 전용 값(02 §4-6) — 입구인 관리 홈 카드와 같은 말을 쓴다
  { value: "NEEDS_RECOMMENDATION_REVIEW", label: "추천 판본 확인 필요" },
  { value: "HIDDEN", label: "숨김" },
];

const LEVEL_OPTIONS = [
  { value: "", label: "전체" },
  { value: "BEGINNER", label: "입문" },
  { value: "ELEMENTARY", label: "초급" },
  { value: "INTERMEDIATE", label: "중급" },
  { value: "ADVANCED", label: "고급" },
  { value: "NONE", label: "미정" },
];

const STATUS_LABELS = {
  READY: "바로 받기 가능",
  PREPARING: "준비 중",
  RESTRICTED: "이용 제한",
  UNKNOWN: "저작권 확인 중",
};

const STATUS_CLASSES = {
  READY: "badge-free",
  PREPARING: "badge-preparing",
  RESTRICTED: "badge-restricted",
  UNKNOWN: "badge-unknown",
};

function pad(value) {
  return String(value).padStart(2, "0");
}

/** 05-D 수정일: 올해면 MM-DD, 다른 해면 YYYY-MM-DD */
function formatWorkListDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  const monthDay = `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return date.getFullYear() === new Date().getFullYear() ? monthDay : `${date.getFullYear()}-${monthDay}`;
}

/**
 * 02 §4-6 recommendationReviewed — 추천이 "있는데" 사람 눈을 통과하지 않은 곡의 표시.
 * 서버가 필드를 안 내리면(undefined) 달지 않는다 — 없는 것을 미검수라고 단정하지 않는다.
 */
function NeedsReviewBadge({ work }) {
  if (!work.hasRecommended || work.recommendationReviewed !== false) return null;
  return <span className="status-badge badge-needs-review">미검수</span>;
}

/**
 * 02 §4-6 recommendationSource — 지금 추천을 누가 골랐나. 값은 넷: 추천 없음(`–`) / 자동 / 사람 / 기록 없음(null).
 * "기록 없음" 은 enum 이 아니라 null 이다 — 추천은 있는데 기록이 없는 실데이터 42곡의 정상 상태(§4-7-2).
 */
function RecommendationSourceCell({ work }) {
  if (!work.hasRecommended) return "–";
  if (work.recommendationSource === "AUTO") return "자동";
  if (work.recommendationSource === "ADMIN") return "사람";
  return "기록 없음";
}

function WorkStatusBadges({ work }) {
  if (work.hidden) {
    return (
      <>
        <span className="status-badge badge-hidden">
          <span className="material-icons" aria-hidden="true">
            visibility_off
          </span>
          숨김
        </span>
        {work.needsWork ? <span className="status-badge badge-needs-work">보완 필요</span> : null}
      </>
    );
  }
  if (work.needsWork) {
    return <span className="status-badge badge-needs-work">보완 필요</span>;
  }
  return (
    <span className={`status-badge ${STATUS_CLASSES[work.status] ?? "badge-unknown"}`}>
      {STATUS_LABELS[work.status] ?? ""}
    </span>
  );
}

/** 05_관리자 화면 D — /admin/works?q=&status=&composerId=&level=&page= (02_API §4-6) */
export function WorkAdminListPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const query = buildWorkQuery(searchParams, QUERY_KEYS);
  const hasFilter = hasAdminFilter(searchParams, FILTER_KEYS);

  const list = useApiResource(() => callApi(`/api/admin/works?${query}`).then((result) => result.data), {
    deps: [query],
  });
  const { options: composerOptions } = useComposerOptions();

  const [text, changeText] = useDebouncedSearchInput(q, (next) =>
    setSearchParams(applyAdminFilter(searchParams, { q: next.trim() })),
  );

  const data = list.data;
  const works = data?.works;
  const rows = works?.content ?? [];
  const isEmpty = Boolean(data) && rows.length === 0;
  const isEmptyAll = isEmpty && !hasFilter;

  const changeFilter = (key, value) => setSearchParams(applyAdminFilter(searchParams, { [key]: value }));

  return (
    <div className="admin-work-list">
      <nav className="breadcrumb">
        <Link to="/admin">관리</Link>
        <span> › </span>
        <span>곡 관리</span>
      </nav>

      <div className="page-header">
        <h1>
          <span className="material-icons">library_music</span>
          곡 관리
        </h1>
        {isEmptyAll ? null : (
          <Link className="btn btn-primary" to="/admin/works/new">
            <span className="material-icons" aria-hidden="true">
              add
            </span>
            새 곡
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
              placeholder="곡 이름, 작곡가, 작품번호"
              aria-label="곡 이름, 작곡가, 작품번호"
              onChange={(event) => changeText(event.target.value)}
            />
          </span>
        </span>

        <span className="admin-filter" data-label="상태">
          <select
            className="filter-select"
            aria-label="상태"
            value={searchParams.get("status") ?? ""}
            onChange={(event) => changeFilter("status", event.target.value)}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </span>

        <span className="admin-filter" data-label="작곡가">
          <select
            className="filter-select"
            aria-label="작곡가"
            value={searchParams.get("composerId") ?? ""}
            onChange={(event) => changeFilter("composerId", event.target.value)}
          >
            <option value="">전체</option>
            {composerOptions.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </span>

        <span className="admin-filter" data-label="난이도">
          <select
            className="filter-select"
            aria-label="난이도"
            value={searchParams.get("level") ?? ""}
            onChange={(event) => changeFilter("level", event.target.value)}
          >
            {LEVEL_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </span>
      </div>

      {list.error ? (
        <ErrorState onRetry={list.reload} />
      ) : !data ? (
        <div className="skeleton-list" aria-hidden="true">
          {SKELETON_ROWS.map((index) => (
            <div key={index} className="skeleton-row" />
          ))}
        </div>
      ) : (
        <>
          <p className="admin-list-count">
            {`${data.unfilteredTotal}곡${hasFilter ? ` 중 ${works?.totalElements ?? 0}곡` : ""}`}
          </p>

          {isEmptyAll ? (
            <EmptyState icon="library_music" title="등록된 곡이 없어요">
              <Link className="btn btn-primary" to="/admin/works/new">
                <span className="material-icons" aria-hidden="true">
                  add
                </span>
                새 곡
              </Link>
              <Link className="btn btn-text" to="/admin/crawl">
                수집 관리로 가기
              </Link>
            </EmptyState>
          ) : isEmpty ? (
            <EmptyState icon="filter_alt_off" title="이 조건에 맞는 곡이 없어요">
              <button className="btn btn-text" type="button" onClick={() => setSearchParams(new URLSearchParams())}>
                필터 해제
              </button>
            </EmptyState>
          ) : (
            <>
              <div className="data-table admin-work-table">
                <div className="data-table-head">
                  <span>제목</span>
                  <span>작곡가</span>
                  <span>작품번호</span>
                  <span>난이도</span>
                  <span>판본</span>
                  <span>추천</span>
                  <span>상태</span>
                  <span>수정일</span>
                </div>
                {rows.map((work) => (
                  <div
                    key={work.id}
                    className="data-table-row admin-row"
                    data-testid={`admin-work-row-${work.id}`}
                    onClick={() => navigate(`/admin/works/${work.id}`)}
                  >
                    <span className={work.titleKo ? "admin-work-title" : "admin-work-title-original"}>
                      {work.titleKo || work.titleOriginal}
                    </span>
                    <span className="admin-work-composer">
                      {work.composer?.nameKo || work.composer?.nameOriginal || "–"}
                    </span>
                    <span className="admin-work-catalog">
                      {work.catalogNumbers?.length ? work.catalogNumbers.join(", ") : "–"}
                    </span>
                    <span className="admin-work-level">
                      <LevelChip level={work.level} />
                    </span>
                    <span className="admin-work-editions">{work.editionCount}</span>
                    <span className="admin-work-recommended">
                      <RecommendationSourceCell work={work} />
                    </span>
                    <span className="admin-work-status">
                      <WorkStatusBadges work={work} />
                      <NeedsReviewBadge work={work} />
                    </span>
                    <span className="admin-work-updated">{formatWorkListDate(work.updatedAt)}</span>
                  </div>
                ))}
              </div>
              <Pagination
                page={works.page}
                totalPages={works.totalPages}
                onChange={(next) => setSearchParams(withPage(searchParams, next))}
              />
            </>
          )}
        </>
      )}
    </div>
  );
}
