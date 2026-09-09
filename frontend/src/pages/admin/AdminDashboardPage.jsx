import { Link } from "react-router-dom";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { callApi } from "../../lib/http.js";
import { formatAdminDateTime, formatCount, formatCrawlJobStatus } from "../../lib/format.js";

const STAT_CARDS = [
  { key: "totalWorks", label: "전체 곡", to: "/admin/works" },
  { key: "readyWorks", label: "바로 받기 가능", to: "/admin/works?status=READY" },
  { key: "preparingWorks", label: "준비 중", to: "/admin/works?status=PREPARING" },
  { key: "needsWorkWorks", label: "보완 필요", to: "/admin/works?status=NEEDS_WORK", tone: "danger" },
  {
    // 02 §4-1 · §5-6-1 — 공개 기준(§8-17 "추천 판본 미검수 0곡")을 재는 숫자. §4-6 목록과 같은 모집단이다
    key: "needsRecommendationReviewWorks",
    label: "추천 판본 확인 필요",
    to: "/admin/works?status=NEEDS_RECOMMENDATION_REVIEW",
    tone: "warning",
  },
  { key: "unknownCopyrightEditions", label: "저작권 확인 중 판본", to: "/admin/copyright", tone: "warning" },
  { key: "monthlyDownloads", label: "이번 달 다운로드", to: null },
];

const SHORTCUTS = [
  { label: "작곡가 관리", icon: "people", to: "/admin/composers" },
  { label: "곡 관리", icon: "library_music", to: "/admin/works" },
  { label: "저작권 판정 대기함", icon: "gavel", to: "/admin/copyright", badgeKey: "unknownCopyrightEditions" },
  { label: "수집 관리", icon: "cloud_download", to: "/admin/crawl" },
];

function StatCard({ label, value, to, tone }) {
  const body = (
    <>
      <span className="stat-card-label">{label}</span>
      <span className={`stat-card-value${tone && value > 0 ? ` stat-card-value-${tone}` : ""}`}>
        {formatCount(value)}
      </span>
    </>
  );
  return to ? (
    <Link className="stat-card" to={to}>
      {body}
    </Link>
  ) : (
    <div className="stat-card">{body}</div>
  );
}

/** 05_관리자_홈.md 화면 A — GET /api/admin/dashboard */
export function AdminDashboardPage() {
  const dashboard = useApiResource(() => callApi("/api/admin/dashboard").then((result) => result.data), { deps: [] });
  const data = dashboard.data;
  const job = data?.latestJob ?? null;
  const isEmptyLibrary = Boolean(data) && data.totalWorks === 0;

  return (
    <div className="admin-dashboard">
      <div className="page-header">
        <h1>
          <span className="material-icons">admin_panel_settings</span>
          관리
        </h1>
      </div>

      {dashboard.error ? (
        <ErrorState onRetry={dashboard.reload} />
      ) : !data ? (
        <div className="stat-card-grid" aria-hidden="true">
          {STAT_CARDS.map((card) => (
            <div key={card.key} className="stat-card skeleton-card" />
          ))}
        </div>
      ) : (
        <>
          <div className="stat-card-grid">
            {STAT_CARDS.map((card) => (
              <StatCard key={card.key} label={card.label} value={data[card.key]} to={card.to} tone={card.tone} />
            ))}
          </div>
          {isEmptyLibrary ? (
            <InlineAlert variant="info">
              첫 곡을 등록해 보세요 — 수집 관리에서 IMSLP 주소를 넣거나, 곡 관리에서 직접 등록할 수 있어요
            </InlineAlert>
          ) : null}
        </>
      )}

      <section className="admin-shortcuts">
        <h2 className="section-title">바로가기</h2>
        <div className="admin-shortcut-list">
          {SHORTCUTS.map((shortcut) => (
            <Link key={shortcut.to} className="btn btn-outline btn-lg admin-shortcut" to={shortcut.to}>
              <span className="material-icons" aria-hidden="true">
                {shortcut.icon}
              </span>
              {shortcut.label}
              {shortcut.badgeKey && data?.[shortcut.badgeKey] ? (
                <span className="admin-shortcut-badge">{formatCount(data[shortcut.badgeKey])}</span>
              ) : null}
            </Link>
          ))}
        </div>
      </section>

      <section className="admin-latest-job">
        <h2 className="section-title">최근 수집 작업</h2>
        {job ? (
          <div className="admin-job-card">
            <span className={`job-status job-status-${job.status.toLowerCase()}`}>{formatCrawlJobStatus(job.status)}</span>
            <span className="admin-job-progress">{`${job.processedCount} / ${job.totalCount}`}</span>
            {job.status === "COMPLETED" ? (
              <span className="admin-job-result">{`성공 ${job.successCount} · 실패 ${job.failCount}`}</span>
            ) : null}
            <span className="admin-job-started">{`시작 ${formatAdminDateTime(job.createdAt)}`}</span>
            <Link className="btn btn-text" to={`/admin/crawl/${job.id}`}>
              보기
              <span className="material-icons" aria-hidden="true">
                chevron_right
              </span>
            </Link>
          </div>
        ) : dashboard.error || !data ? null : (
          <div className="admin-job-empty">
            <p>아직 수집한 적이 없어요</p>
            <Link className="btn btn-text" to="/admin/crawl">
              수집 시작하기
            </Link>
          </div>
        )}
      </section>
    </div>
  );
}
