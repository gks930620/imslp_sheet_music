import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { callApi } from "../../../lib/http.js";

const POLL_INTERVAL_MS = 30_000;

/**
 * 00_공통 §3-14 AdminBanner — 관리 화면 상단 수집 진행 띠.
 * 진입 시 + 30초 간격으로 진행 중 작업을 조회한다 (02_API §7).
 * 조회 실패는 조용히 무시한다 (띠는 보조 정보).
 */
export function AdminBanner({ showLink = true }) {
  const [job, setJob] = useState(null);

  useEffect(() => {
    let alive = true;

    const load = async () => {
      try {
        const result = await callApi("/api/admin/crawl/jobs/active");
        if (alive) setJob(result?.data ?? null);
      } catch {
        if (alive) setJob(null);
      }
    };

    load();
    const timer = setInterval(load, POLL_INTERVAL_MS);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, []);

  if (!job) return null;

  const message =
    job.status === "PAUSED"
      ? "수집 일시 정지 — IMSLP가 응답하지 않아요 — "
      : `수집 진행 중 ${job.processedCount}/${job.totalCount} — `;

  return (
    <div className={`admin-banner${job.status === "PAUSED" ? " admin-banner-paused" : ""}`}>
      <span className="material-icons" aria-hidden="true">
        {job.status === "PAUSED" ? "pause_circle" : "cloud_download"}
      </span>
      <span className="admin-banner-text">{message}</span>
      {showLink ? (
        <Link className="admin-banner-link" to={`/admin/crawl/${job.id}`}>
          보기
        </Link>
      ) : null}
    </div>
  );
}
