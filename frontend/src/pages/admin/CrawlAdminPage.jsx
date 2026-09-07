import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { CrawlCheckTable } from "../../components/sheetmusic/admin/CrawlCheckTable.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { callApi } from "../../lib/http.js";
import { formatCrawlJobStatus, formatElapsedTime, formatEstimatedTime, formatShortDateTime } from "../../lib/format.js";

const URL_LABEL = "IMSLP 작품 페이지 주소를 한 줄에 하나씩 붙여넣어 주세요";
const URL_PLACEHOLDER = "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)";
const NOTICE_PACE = "IMSLP 서버에 부담을 주지 않도록 요청 사이에 2초 이상 쉬며 천천히 가져옵니다.";
const NOTICE_WITH_FILES = "곡당 보통 1~2분, 파일이 크면 더 걸립니다.";
const NOTICE_WITHOUT_FILES = "파일을 받지 않으므로 곡당 몇 초면 끝납니다.";
const NOTICE_BACKGROUND = "화면을 닫아도 수집은 계속됩니다.";
const PLANNED_VERDICTS = ["NEW", "ATTACH", "EXISTS"];

/** 07_관리자_수집.md 화면 A — /admin/crawl */
export function CrawlAdminPage() {
  const navigate = useNavigate();
  const [text, setText] = useState("");
  const [fetchFiles, setFetchFiles] = useState(true);
  const [check, setCheck] = useState(null);
  const [refreshSeqs, setRefreshSeqs] = useState([]);
  const [stale, setStale] = useState(false);
  const [checking, setChecking] = useState(false);
  const [checkError, setCheckError] = useState("");
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState("");
  const [conflict, setConflict] = useState(false);
  const [page, setPage] = useState(0);

  const jobs = useApiResource(() => callApi(`/api/admin/crawl/jobs?page=${page}`).then((result) => result.data), {
    deps: [page],
  });
  const active = useApiResource(() => callApi("/api/admin/crawl/jobs/active").then((result) => result.data ?? null), {
    deps: [],
  });
  const activeJob = active.data ?? null;

  const lines = text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const summary = check?.summary ?? null;
  const plannedCount = summary ? summary.newCount + summary.attachCount : 0;
  const refreshCount = refreshSeqs.length;
  const blocked = Boolean(activeJob) || conflict;
  const canStart = Boolean(check) && !stale && !checking && !starting && plannedCount + refreshCount > 0 && !blocked;

  const changeText = (value) => {
    setText(value);
    if (check) setStale(true);
  };

  const changeFetchFiles = (value) => {
    setFetchFiles(value);
    if (check) setStale(true);
  };

  const runCheck = async () => {
    setChecking(true);
    setCheckError("");
    try {
      const result = await callApi("/api/admin/crawl/check", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ urls: lines, fetchFiles }),
      });
      setCheck(result.data);
      setRefreshSeqs([]);
      setStale(false);
    } catch {
      setCheckError("주소를 확인하지 못했어요. 잠시 후 다시 시도해 주세요");
    } finally {
      setChecking(false);
    }
  };

  const start = async () => {
    setStarting(true);
    setStartError("");
    try {
      const items = (check?.items ?? [])
        .filter((item) => PLANNED_VERDICTS.includes(item.verdict))
        .map((item) => ({ url: item.canonicalUrl ?? item.inputUrl, refresh: refreshSeqs.includes(item.seq) }));
      const result = await callApi("/api/admin/crawl/jobs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ items, fetchFiles }),
      });
      navigate(`/admin/crawl/${result.data.id}`);
    } catch (error) {
      if (error?.status === 409) setConflict(true);
      else setStartError("수집을 시작하지 못했어요. 잠시 후 다시 시도해 주세요");
    } finally {
      setStarting(false);
    }
  };

  const estimatedText = stale
    ? "다시 확인해 주세요"
    : `예상 소요 ${formatEstimatedTime(summary?.estimatedSeconds) ?? "계산 중"}`;

  return (
    <div className="crawl-admin">
      <nav className="breadcrumb">
        <Link to="/admin">관리</Link>
        <span> › </span>
        <span>수집 관리</span>
      </nav>

      <div className="page-header">
        <h1>
          <span className="material-icons">cloud_download</span>
          수집 관리
        </h1>
      </div>

      <section className="panel crawl-register">
        <h2 className="panel-title">수집 대상 등록</h2>

        <div className="form-group">
          <label className="form-label" htmlFor="crawlUrls">
            {URL_LABEL}
          </label>
          <textarea
            id="crawlUrls"
            className="form-textarea crawl-urls"
            rows={8}
            placeholder={URL_PLACEHOLDER}
            value={text}
            onChange={(event) => changeText(event.target.value)}
          />
          <div className="crawl-register-actions">
            <span className="form-help">{`${lines.length}줄`}</span>
            <button className="btn btn-secondary" type="button" disabled={lines.length === 0 || checking} onClick={runCheck}>
              {checking ? "확인 중…" : "확인"}
            </button>
          </div>
        </div>

        <label className="crawl-fetch-files">
          <input type="checkbox" checked={fetchFiles} onChange={(event) => changeFetchFiles(event.target.checked)} />
          <span>파일도 함께 받기</span>
        </label>
        <p className="form-help">
          끄면 판본 정보만 가져오고 PDF는 받지 않아요 — 나중에 판본별 &apos;파일 받아오기&apos;로 받을 수 있어요
        </p>

        {checkError ? <InlineAlert variant="danger">{checkError}</InlineAlert> : null}

        {check ? (
          <>
            <CrawlCheckTable
              items={check.items}
              refreshSeqs={refreshSeqs}
              onToggleRefresh={(seq, checked) =>
                setRefreshSeqs((prev) => (checked ? [...prev, seq] : prev.filter((item) => item !== seq)))
              }
            />
            <p className="crawl-check-summary">
              {`수집 예정 ${plannedCount}건`}
              {summary.attachCount > 0 ? ` (새 곡 ${summary.newCount} · 등록된 곡에 붙임 ${summary.attachCount})` : ""}
              {` · 건너뜀 ${summary.existsCount}건 · 오류 ${summary.invalidCount}건`}
              {refreshCount > 0 ? ` · 정보 갱신 ${refreshCount}건` : ""}
              {` · ${estimatedText}`}
            </p>
            {plannedCount + refreshCount === 0 ? <p className="form-help">수집할 주소가 없어요</p> : null}
          </>
        ) : null}

        <InlineAlert variant="info">
          {`${NOTICE_PACE} ${fetchFiles ? NOTICE_WITH_FILES : NOTICE_WITHOUT_FILES} ${NOTICE_BACKGROUND}`}
        </InlineAlert>

        {blocked ? (
          <InlineAlert variant="warning">
            {"진행 중인 수집이 끝나면 시작할 수 있어요"}
            {activeJob ? (
              <>
                {" — "}
                <Link className="btn btn-text" to={`/admin/crawl/${activeJob.id}`}>
                  보기
                </Link>
              </>
            ) : null}
          </InlineAlert>
        ) : null}

        {startError ? <InlineAlert variant="danger">{startError}</InlineAlert> : null}

        <div className="crawl-start-actions">
          <button className="btn btn-primary btn-lg" type="button" disabled={!canStart} onClick={start}>
            {starting ? "시작하는 중…" : "수집 시작"}
          </button>
        </div>
      </section>

      <section className="crawl-jobs">
        <h2 className="section-title">수집 작업 목록</h2>
        {jobs.error ? (
          <ErrorState onRetry={jobs.reload} />
        ) : !jobs.data ? (
          <div className="skeleton-list" aria-hidden="true">
            {[0, 1, 2].map((index) => (
              <div key={index} className="skeleton-row" />
            ))}
          </div>
        ) : jobs.data.content.length === 0 ? (
          <p className="crawl-jobs-empty">아직 수집한 적이 없어요</p>
        ) : (
          <>
            <div className="data-table crawl-jobs-table">
              <div className="data-table-head" aria-hidden="true">
                <span>상태</span>
                <span>대상</span>
                <span>성공</span>
                <span>실패</span>
                <span>건너뜀</span>
                <span>시작</span>
                <span>걸린 시간</span>
                <span />
              </div>
              {jobs.data.content.map((job) => (
                <div key={job.id} className="data-table-row" data-testid={`crawl-job-row-${job.id}`}>
                  <span className={`job-status job-status-${job.status.toLowerCase()}`}>
                    {formatCrawlJobStatus(job.status)}
                  </span>
                  <span>{job.totalCount}</span>
                  <span>{job.successCount}</span>
                  <span>{job.failCount}</span>
                  <span>{job.skipCount}</span>
                  <span>{formatShortDateTime(job.createdAt)}</span>
                  <span>
                    {job.status === "RUNNING" || job.status === "PAUSED" ? "–" : formatElapsedTime(job.elapsedSeconds)}
                  </span>
                  {/* 행의 "보기"는 진행 중 안내의 "보기"(정확히 그 문구)와 구분돼야 하므로 화살표를 이름에 남긴다 */}
                  <Link className="btn btn-text" to={`/admin/crawl/${job.id}`}>
                    보기
                    <span className="material-icons">chevron_right</span>
                  </Link>
                </div>
              ))}
            </div>
            <Pagination page={jobs.data.page} totalPages={jobs.data.totalPages} onChange={setPage} />
          </>
        )}
      </section>
    </div>
  );
}
