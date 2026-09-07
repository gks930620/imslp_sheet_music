import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ConfirmDialog } from "../../components/common/ConfirmDialog.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { NotFoundView } from "../../components/common/NotFoundView.jsx";
import { callApi } from "../../lib/http.js";
import {
  formatClockTime,
  formatCrawlJobStatus,
  formatElapsedTime,
  formatEstimatedTime,
  formatShortDateTime,
} from "../../lib/format.js";

const POLL_INTERVAL_MS = 10_000;
const PROGRESS_STATUSES = ["RUNNING", "PAUSED", "STOPPED"];
const POLLING_STATUSES = ["RUNNING", "PAUSED"];

const ITEM_STATUS_LABELS = {
  PENDING: "대기",
  PROCESSING: "처리 중",
  SUCCESS: "성공",
  FAILED: "실패",
  SKIPPED: "건너뜀",
  HIDDEN: "숨김",
};

const STAGE_LABELS = {
  READING_METADATA: "판본 정보 읽는 중",
  MAKING_PREVIEW: "미리보기 만드는 중",
};

const ITEM_FILTERS = {
  전체: () => true,
  실패: (item) => item.status === "FAILED" || item.status === "HIDDEN",
  성공: (item) => item.status === "SUCCESS",
  "처리 중·대기": (item) => item.status === "PROCESSING" || item.status === "PENDING",
  건너뜀: (item) => item.status === "SKIPPED",
};

function stageText(job) {
  if (!job.currentItem || !job.currentStage) return "다음 항목 준비 중";
  const title = job.currentItem.title || job.currentItem.url;
  const stage =
    job.currentStage === "DOWNLOADING_FILE"
      ? `파일 받는 중 (${job.currentFileIndex}/${job.currentFileTotal})`
      : STAGE_LABELS[job.currentStage] ?? "";
  return `지금 처리 중: ${title} — ${stage}`;
}

function itemSuffix(item) {
  if (item.failReason === "FILE_DOWNLOAD_FAILED") return " (파일 받아오기로 재시도)";
  if (item.status === "HIDDEN") return " (확인 후 숨김 해제)";
  return "";
}

function ItemTable({ items }) {
  return (
    <div className="data-table crawl-item-table">
      <div className="data-table-head" aria-hidden="true">
        <span>#</span>
        <span>IMSLP 주소</span>
        <span>상태</span>
        <span>결과</span>
      </div>
      {items.map((item) => (
        <div key={item.id} className="data-table-row" data-testid={`crawl-item-row-${item.seq}`}>
          <span>{item.seq}</span>
          <span className="crawl-item-url" title={item.url}>
            {item.url}
          </span>
          <span className={`item-status item-status-${item.status.toLowerCase()}`}>
            {ITEM_STATUS_LABELS[item.status] ?? item.status}
          </span>
          <span className="crawl-item-result">
            {item.message ?? ""}
            {item.workId ? (
              <>
                {" → "}
                <Link to={`/admin/works/${item.workId}`}>곡 보기</Link>
              </>
            ) : null}
            {itemSuffix(item)}
          </span>
        </div>
      ))}
    </div>
  );
}

/** 07_관리자_수집.md 화면 B — /admin/crawl/:jobId (진행 모드 / 결과 모드) */
export function CrawlProgressPage() {
  const { jobId } = useParams();
  const navigate = useNavigate();
  const [job, setJob] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [pollError, setPollError] = useState(false);
  const [activeJob, setActiveJob] = useState(null);
  const [lastLoadedAt, setLastLoadedAt] = useState(null);
  const [itemFilter, setItemFilter] = useState("전체");
  const [confirm, setConfirm] = useState(null);
  const [stopping, setStopping] = useState(false);
  const [actionError, setActionError] = useState("");
  const jobRef = useRef(null);

  // 폴링 실패 때 "첫 진입 실패"와 "현황 갱신 실패"를 가르기 위해 마지막 성공 값을 들고 있는다
  useEffect(() => {
    jobRef.current = job;
  }, [job]);

  const load = useCallback(
    () =>
      callApi(`/api/admin/crawl/jobs/${jobId}`)
        .then((result) => {
          setJob(result.data);
          setLastLoadedAt(new Date().toISOString());
          setLoadError(null);
          setPollError(false);
        })
        .catch((error) => {
          // 이미 현황을 그린 뒤라면 화면을 유지하고 갱신 실패만 알린다 (07 상태별 UI)
          if (jobRef.current) setPollError(true);
          else setLoadError(error ?? { message: "오류" });
        }),
    [jobId],
  );

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    let alive = true;
    callApi("/api/admin/crawl/jobs/active")
      .then((result) => {
        if (alive) setActiveJob(result?.data ?? null);
      })
      .catch(() => {
        if (alive) setActiveJob(null);
      });
    return () => {
      alive = false;
    };
  }, [jobId]);

  const polling = Boolean(job) && POLLING_STATUSES.includes(job.status);
  useEffect(() => {
    if (!polling) return undefined;
    const timer = setInterval(load, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [polling, load]);

  const runAction = async (path, failMessage) => {
    setActionError("");
    try {
      const result = await callApi(`/api/admin/crawl/jobs/${jobId}/${path}`, { method: "POST" });
      return result.data;
    } catch {
      setActionError(failMessage);
      return null;
    }
  };

  if (loadError) {
    return loadError.status === 404 ? (
      <NotFoundView />
    ) : (
      <ErrorState
        onRetry={() => {
          setLoadError(null);
          load();
        }}
      />
    );
  }

  if (!job) {
    return (
      <div className="skeleton-detail" aria-hidden="true">
        <div className="skeleton-card" />
        <div className="skeleton-row" />
      </div>
    );
  }

  const isProgressMode = PROGRESS_STATUSES.includes(job.status);
  const items = (job.items ?? []).filter(ITEM_FILTERS[itemFilter] ?? ITEM_FILTERS.전체);
  const failedItems = items.filter((item) => item.status === "FAILED" || item.status === "HIDDEN");
  const otherItems = items.filter((item) => item.status !== "FAILED" && item.status !== "HIDDEN");
  const otherActiveJob = activeJob && String(activeJob.id) !== String(job.id) ? activeJob : null;

  const summary = isProgressMode
    ? [
        `${job.processedCount} / ${job.totalCount} 완료`,
        `성공 ${job.successCount}`,
        `실패 ${job.failCount}`,
        job.skipCount > 0 ? `건너뜀 ${job.skipCount}` : "",
        job.status === "STOPPED" ? `대기 ${job.pendingCount}` : "",
        job.estimatedRemainingSeconds !== null && job.estimatedRemainingSeconds !== undefined
          ? `남은 예상 시간 ${formatEstimatedTime(job.estimatedRemainingSeconds)}`
          : "",
      ]
        .filter(Boolean)
        .join(" · ")
    : [
        `총 ${job.totalCount}건 — 성공 ${job.successCount}`,
        `실패 ${job.failCount}`,
        job.skipCount > 0 ? `건너뜀 ${job.skipCount}` : "",
        `걸린 시간 ${formatElapsedTime(job.elapsedSeconds)}`,
      ]
        .filter(Boolean)
        .join(" · ");

  return (
    <div className="crawl-progress">
      <nav className="breadcrumb">
        <Link to="/admin">관리</Link>
        <span> › </span>
        <Link to="/admin/crawl">수집 관리</Link>
        <span>{` › 수집 작업 #${job.id}`}</span>
      </nav>

      <div className="page-header crawl-progress-header">
        <h1>
          <span className="material-icons">cloud_download</span>
          {isProgressMode ? "수집 진행" : "수집 결과"}
        </h1>
        <div className="crawl-progress-actions">
          {isProgressMode ? (
            <button className="btn btn-outline" type="button" onClick={load}>
              <span className="material-icons" aria-hidden="true">
                refresh
              </span>
              새로고침
            </button>
          ) : null}
          {job.status === "RUNNING" || job.status === "PAUSED" ? (
            <button
              className="btn btn-danger"
              type="button"
              disabled={stopping || job.stopRequested}
              onClick={() => setConfirm("stop")}
            >
              <span className="material-icons" aria-hidden="true">
                stop
              </span>
              {stopping || job.stopRequested ? "중지하는 중…" : "중지"}
            </button>
          ) : null}
          {job.status === "PAUSED" ? (
            <button
              className="btn btn-outline"
              type="button"
              onClick={async () => {
                const next = await runAction("resume", "이어서 시작하지 못했어요 — 다시 시도");
                if (next) load();
              }}
            >
              지금 이어서 시작
            </button>
          ) : null}
          {job.status === "STOPPED" ? (
            <button
              className="btn btn-primary"
              type="button"
              disabled={Boolean(otherActiveJob)}
              onClick={async () => {
                const next = await runAction("resume", "이어서 시작하지 못했어요 — 다시 시도");
                if (next) load();
              }}
            >
              <span className="material-icons" aria-hidden="true">
                play_arrow
              </span>
              이어서 시작
            </button>
          ) : null}
          {!isProgressMode && job.failCount > 0 ? (
            <button className="btn btn-primary" type="button" onClick={() => setConfirm("retry")}>
              <span className="material-icons" aria-hidden="true">
                replay
              </span>
              실패한 것만 다시 시도
            </button>
          ) : null}
        </div>
      </div>

      <section className="crawl-progress-card">
        <p className={`job-status job-status-${job.status.toLowerCase()}`}>{formatCrawlJobStatus(job.status)}</p>

        {isProgressMode ? (
          <div className="progress-row">
            <div
              className="progress-bar"
              role="progressbar"
              aria-valuenow={job.processedCount}
              aria-valuemin={0}
              aria-valuemax={job.totalCount}
            >
              <span
                className="progress-bar-fill"
                style={{ width: `${job.totalCount ? (job.processedCount / job.totalCount) * 100 : 0}%` }}
              />
            </div>
            <span className="progress-count">{`${job.processedCount} / ${job.totalCount}`}</span>
          </div>
        ) : null}

        <p className="crawl-progress-summary">{summary}</p>

        {!isProgressMode ? (
          <p className="crawl-progress-times">
            {`시작 ${formatShortDateTime(job.startedAt ?? job.createdAt)} · 종료 ${formatShortDateTime(job.finishedAt)}`}
          </p>
        ) : null}

        {job.status === "RUNNING" || job.status === "PAUSED" ? <p className="crawl-progress-now">{stageText(job)}</p> : null}

        {job.status === "FAILED" && job.failureReason ? (
          <InlineAlert variant="danger">{job.failureReason}</InlineAlert>
        ) : null}

        {job.status === "PAUSED" ? (
          <InlineAlert variant="warning">
            {`IMSLP가 응답하지 않아요. 10분 뒤 자동으로 다시 시도합니다 (다음 시도 ${formatClockTime(job.pausedUntil)})`}
          </InlineAlert>
        ) : null}

        {job.status === "STOPPED" && job.stoppedByRestart ? (
          <InlineAlert variant="info">
            서비스가 재시작되어 멈췄어요. &apos;이어서 시작&apos;하면 대기 항목부터 계속하고, 이미 받은 파일은 다시 받지
            않아요
          </InlineAlert>
        ) : null}

        {otherActiveJob && job.status === "STOPPED" ? (
          <InlineAlert variant="warning">
            {"진행 중인 수집이 끝나면 시작할 수 있어요 — "}
            <Link className="btn btn-text" to={`/admin/crawl/${otherActiveJob.id}`}>
              보기
            </Link>
          </InlineAlert>
        ) : null}

        {actionError ? <InlineAlert variant="danger">{actionError}</InlineAlert> : null}

        {pollError ? (
          <InlineAlert variant="danger">
            {"현황을 못 가져왔어요 — "}
            <button className="btn btn-text" type="button" onClick={load}>
              새로고침
            </button>
          </InlineAlert>
        ) : null}

        {POLLING_STATUSES.includes(job.status) ? (
          <p className="crawl-progress-refresh-note">
            {`10초마다 자동으로 갱신돼요 · 마지막 갱신 ${formatClockTime(lastLoadedAt, { seconds: true })}`}
          </p>
        ) : null}
      </section>

      {!isProgressMode && job.successCount > 0 ? (
        <InlineAlert variant="warning">
          <p>
            {"성공한 곡은 한국어 제목·별칭·난이도가 비어 있어 '보완 필요' 상태예요 → "}
            <Link to="/admin/works?status=NEEDS_WORK">곡 관리에서 보완하기</Link>
          </p>
        </InlineAlert>
      ) : null}

      <section className="crawl-items">
        <div className="crawl-items-head">
          <h2 className="section-title">{`항목 (${job.totalCount})`}</h2>
          <select
            className="filter-select"
            aria-label="항목 필터"
            value={itemFilter}
            onChange={(event) => setItemFilter(event.target.value)}
          >
            {Object.keys(ITEM_FILTERS).map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>

        {isProgressMode ? (
          <ItemTable items={items} />
        ) : (
          <>
            {job.failCount > 0 ? (
              <>
                <h3 className="crawl-items-group">{`실패 (${job.failCount})`}</h3>
                <ItemTable items={failedItems} />
              </>
            ) : null}
            <h3 className="crawl-items-group">{`성공 (${job.successCount}) · 건너뜀 (${job.skipCount})`}</h3>
            <ItemTable items={otherItems} />
          </>
        )}
      </section>

      {confirm === "stop" ? (
        <ConfirmDialog
          title="수집을 중지할까요?"
          description="지금 처리 중인 곡은 끝까지 마친 뒤 멈춰요. 남은 항목은 '대기'로 남고, 나중에 이어서 시작할 수 있어요"
          cancelLabel="계속 진행"
          confirmLabel="중지"
          danger
          onCancel={() => setConfirm(null)}
          onConfirm={async () => {
            setConfirm(null);
            setStopping(true);
            const next = await runAction("stop", "중지하지 못했어요 — 다시 시도");
            if (next) setJob((prev) => ({ ...prev, ...next, items: prev.items }));
            else setStopping(false);
          }}
        />
      ) : null}

      {confirm === "retry" ? (
        <ConfirmDialog
          title={`실패한 ${job.failCount}건으로 새 수집 작업을 만들까요?`}
          confirmLabel="만들기"
          onCancel={() => setConfirm(null)}
          onConfirm={async () => {
            setConfirm(null);
            const next = await runAction("retry-failed", "다시 시도하지 못했어요 — 다시 시도");
            if (next) navigate(`/admin/crawl/${next.id}`);
          }}
        />
      ) : null}
    </div>
  );
}
