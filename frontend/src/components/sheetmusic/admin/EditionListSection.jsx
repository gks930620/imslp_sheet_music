import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ConfirmDialog } from "../../common/ConfirmDialog.jsx";
import { EmptyState } from "../../common/EmptyState.jsx";
import { InlineAlert } from "../../common/InlineAlert.jsx";
import { CopyrightBadge } from "../CopyrightBadge.jsx";
import { EditionFormModal } from "./EditionFormModal.jsx";
import { RecommendChangePanel } from "./RecommendChangePanel.jsx";
import { RecommendationReasonBox } from "./RecommendationReasonBox.jsx";
import { callApi } from "../../../lib/http.js";
import { formatCount, formatEditionKind, formatEditionScope, formatFileSizeCompact } from "../../../lib/format.js";

const POLL_INTERVAL_MS = 3_000;
const FETCHING_STATUSES = ["QUEUED", "FETCHING"];
const ACTION_ERROR = "처리하지 못했어요. 잠시 후 다시 시도해 주세요";

function editionLabel(edition) {
  return [formatEditionKind(edition?.kind), edition?.publisher].filter(Boolean).join(" · ");
}

function metaLine(edition) {
  return [edition.publisher || "–", edition.editor || "–", edition.publishYear ?? "–"].join(" / ");
}

/**
 * 06_관리자_판본관리.md 화면 A — 곡 편집 화면(/admin/works/:id) 아래 판본 목록.
 * 목록 자체는 곡 상세(§4-7)가 준 것을 그대로 그리고(정렬도 서버 몫), 바뀌면 onChanged 로 부모가 다시 부른다.
 */
export function EditionListSection({
  workId,
  editions = [],
  recommendedEditionId = null,
  candidateEditionId = null,
  recommendationReviewed = false,
  recommendation,
  composer = null,
  onChanged,
  onToast,
}) {
  const [modal, setModal] = useState(null);
  const [confirmTarget, setConfirmTarget] = useState(null);
  const [rowAlerts, setRowAlerts] = useState({});
  const [fetchingIds, setFetchingIds] = useState([]);
  const [activeCrawlJob, setActiveCrawlJob] = useState(null);
  const [openPanelEditionId, setOpenPanelEditionId] = useState(null);
  const timersRef = useRef({});
  const pollRef = useRef(null);

  const activeFetchIds = useMemo(() => {
    const fromServer = editions.filter((item) => FETCHING_STATUSES.includes(item.fileFetchStatus)).map((item) => item.id);
    return Array.from(new Set([...fromServer, ...fetchingIds]));
  }, [editions, fetchingIds]);

  const hasFetchingRow = activeFetchIds.length > 0;

  const setRowAlert = (editionId, alert) => setRowAlerts((prev) => ({ ...prev, [editionId]: alert }));

  /** 02_API §5-7 — 3초 간격 폴링 한 번 */
  const pollFetchStatus = async (editionId) => {
    try {
      const result = await callApi(`/api/admin/editions/${editionId}`);
      const data = result.data;
      if (data?.fileFetchStatus === "FAILED") {
        setFetchingIds((prev) => prev.filter((id) => id !== editionId));
        setRowAlert(editionId, { kind: "fetch-failed", detail: data.fileFetchError ?? "" });
        return;
      }
      if (!data?.fileFetchStatus && data?.hasFile) {
        setFetchingIds((prev) => prev.filter((id) => id !== editionId));
        const size = formatFileSizeCompact(data.fileSize);
        onToast?.(`파일을 받아왔어요 (${data.pageCount}쪽${size ? ` · ${size}` : ""})`);
        onChanged?.();
      }
    } catch {
      /* 폴링 실패는 다음 주기에 다시 본다 */
    }
  };

  useEffect(() => {
    pollRef.current = pollFetchStatus;
  });

  useEffect(() => {
    activeFetchIds.forEach((editionId) => {
      if (timersRef.current[editionId]) return;
      timersRef.current[editionId] = setInterval(() => pollRef.current?.(editionId), POLL_INTERVAL_MS);
    });
    Object.keys(timersRef.current).forEach((key) => {
      if (activeFetchIds.includes(Number(key))) return;
      clearInterval(timersRef.current[key]);
      delete timersRef.current[key];
    });
  }, [activeFetchIds]);

  useEffect(() => {
    const timers = timersRef.current;
    return () => Object.values(timers).forEach((timer) => clearInterval(timer));
  }, []);

  /** 02_API §5-7 — 받아오는 중인 행이 생기면 진행 중인 수집을 '한 번만' 확인한다(폴링하지 않는다) */
  useEffect(() => {
    if (!hasFetchingRow) return undefined;
    let cancelled = false;
    callApi("/api/admin/crawl/jobs/active")
      .then((result) => {
        if (!cancelled) setActiveCrawlJob(result.data ?? null);
      })
      .catch(() => {
        if (!cancelled) setActiveCrawlJob(null); // 안내 문구용 보조 정보 — 실패하면 기본 문구를 쓴다
      });
    return () => {
      cancelled = true;
    };
  }, [hasFetchingRow]);

  /**
   * 02 §5-6 — 2026-09-21부터 "추천으로 지정" 은 즉시 PUT 하지 않는다. 그 행 아래 A-3 패널을 토글하고,
   * 실제 지정·경고 읽기·사유·Toast 는 패널(RecommendChangePanel)이 맡는다(화면정의 06 A-2·A-3).
   */
  const togglePanel = (edition) => {
    setOpenPanelEditionId((prev) => (prev === edition.id ? null : edition.id));
  };

  const recommendDone = (edition) => (result) => {
    setOpenPanelEditionId(null);
    const previous = editions.find((item) => item.id === result?.previousEditionId);
    onToast?.(
      previous
        ? `추천 판본을 ${editionLabel(previous)}에서 ${editionLabel(edition)}로 바꿨어요`
        : "추천 판본으로 지정했어요",
    );
    onChanged?.();
  };

  const remove = async (edition) => {
    setConfirmTarget(null);
    setRowAlert(edition.id, null);
    try {
      await callApi(`/api/admin/editions/${edition.id}`, { method: "DELETE" });
      onToast?.("삭제했어요");
      onChanged?.();
    } catch {
      setRowAlert(edition.id, { kind: "action-failed" });
    }
  };

  const fetchFile = async (edition) => {
    setRowAlert(edition.id, null);
    setFetchingIds((prev) => (prev.includes(edition.id) ? prev : [...prev, edition.id]));
    try {
      await callApi(`/api/admin/editions/${edition.id}/fetch-file`, { method: "POST" });
    } catch (error) {
      if (error?.status === 409) return; // 이미 받는 중 — 그대로 스피너 유지
      setFetchingIds((prev) => prev.filter((id) => id !== edition.id));
      setRowAlert(edition.id, { kind: "fetch-error", message: error?.message ?? ACTION_ERROR });
    }
  };

  const savedEdition = (saved) => {
    setModal(null);
    onToast?.(
      saved?.workStatus === "READY" && saved?.isRecommended
        ? "저장했어요 — 사용자 화면에서 다운로드가 열렸어요"
        : "저장했어요",
    );
    onChanged?.();
  };

  const recommended = editions.find((item) => item.id === recommendedEditionId) ?? null;
  const needsRecommendation = editions.length > 0 && !recommended;
  const needsJudgement = Boolean(recommended) && recommended.koreaCopyright !== "FREE";

  return (
    <section className="edition-section">
      <div className="edition-section-head">
        <h2 className="section-title">{`판본 (${editions.length}개)`}</h2>
        <button className="btn btn-primary" type="button" onClick={() => setModal({ mode: "create" })}>
          <span className="material-icons" aria-hidden="true">
            add
          </span>
          판본 추가
        </button>
      </div>

      {needsRecommendation ? (
        <InlineAlert variant="warning">추천 판본이 없어요 — 사용자에게는 &apos;준비 중&apos;으로 보여요</InlineAlert>
      ) : null}

      {recommendation !== undefined ? (
        <RecommendationReasonBox
          workId={workId}
          hasRecommendation={Boolean(recommended)}
          reviewed={recommendationReviewed}
          recommendation={recommendation}
          recommendedEdition={recommended}
          onChanged={onChanged}
          onToast={onToast}
        />
      ) : null}

      {needsJudgement ? (
        <InlineAlert variant="warning">
          추천 판본의 저작권이 확정되지 않아 다운로드가 열리지 않아요
          <button
            className="btn btn-text"
            type="button"
            onClick={() => setModal({ mode: "edit", edition: recommended })}
          >
            판정하기
          </button>
        </InlineAlert>
      ) : null}

      {editions.length === 0 ? (
        <EmptyState icon="picture_as_pdf" title="아직 판본이 없어요" description="PDF를 올리거나 수집으로 가져와요">
          <button className="btn btn-primary" type="button" onClick={() => setModal({ mode: "create" })}>
            <span className="material-icons" aria-hidden="true">
              add
            </span>
            판본 추가
          </button>
        </EmptyState>
      ) : (
        <div className="edition-admin-list">
          {editions.map((edition) => {
            const isRecommended = edition.id === recommendedEditionId;
            const isCandidate = edition.id === candidateEditionId;
            const isFetching = activeFetchIds.includes(edition.id);
            const alert = rowAlerts[edition.id];
            return (
              <div
                key={edition.id}
                className={`edition-admin-row${isRecommended ? " edition-admin-row-recommended" : ""}${
                  isCandidate ? " edition-admin-row-candidate" : ""
                }`}
                data-testid={`admin-edition-row-${edition.id}`}
              >
                {isRecommended ? (
                  <span className="status-badge badge-recommended">
                    <span className="material-icons" aria-hidden="true">
                      star
                    </span>
                    추천
                  </span>
                ) : null}
                {isCandidate ? (
                  <span className="status-badge badge-candidate">
                    <span className="material-icons" aria-hidden="true">
                      auto_awesome
                    </span>
                    추천 후보
                  </span>
                ) : null}

                {edition.previewUrl ? (
                  <img className="edition-admin-thumb" src={edition.previewUrl} alt="" />
                ) : (
                  <span className="edition-admin-thumb edition-admin-thumb-empty" aria-hidden="true">
                    –
                  </span>
                )}

                <p className="edition-admin-title">
                  {`${formatEditionKind(edition.kind)} · ${formatEditionScope(edition)}`}
                </p>
                <p className="edition-admin-file">
                  {edition.hasFile
                    ? `${edition.pageCount}쪽 · ${formatFileSizeCompact(edition.fileSize)}`
                    : "파일 없음"}
                </p>
                <p className="edition-admin-meta">{metaLine(edition)}</p>
                <p className="edition-admin-copyright">
                  <CopyrightBadge koreaCopyright={edition.koreaCopyright} />
                  {edition.downloadCount > 0 ? (
                    <span className="edition-admin-downloads">{`다운로드 ${formatCount(edition.downloadCount)}`}</span>
                  ) : null}
                  {edition.imslpDownloadCount > 0 ? (
                    <span className="edition-admin-downloads">
                      {`IMSLP 다운로드 ${formatCount(edition.imslpDownloadCount)}`}
                    </span>
                  ) : null}
                </p>

                <div className="edition-admin-actions">
                  {!edition.hasFile && edition.imslpFileId ? (
                    <button
                      className="btn btn-outline"
                      type="button"
                      disabled={isFetching}
                      onClick={() => fetchFile(edition)}
                    >
                      {isFetching ? "받아오는 중…" : "파일 받아오기"}
                    </button>
                  ) : null}

                  {!isRecommended ? (
                    <>
                      <button
                        className={isCandidate ? "btn btn-primary" : "btn btn-text"}
                        type="button"
                        disabled={!edition.hasFile}
                        aria-expanded={edition.hasFile ? openPanelEditionId === edition.id : undefined}
                        onClick={() => togglePanel(edition)}
                      >
                        {isCandidate ? "이 후보를 추천으로 지정" : "추천으로 지정"}
                      </button>
                      {!edition.hasFile ? (
                        <span className="form-help">파일이 없어 추천으로 지정할 수 없어요</span>
                      ) : null}
                    </>
                  ) : null}

                  <button className="btn btn-text" type="button" onClick={() => setModal({ mode: "edit", edition })}>
                    수정
                  </button>
                  <button className="btn btn-text btn-text-danger" type="button" onClick={() => setConfirmTarget(edition)}>
                    삭제
                  </button>
                </div>

                {isFetching ? (
                  <p className="edition-admin-status">
                    {activeCrawlJob ? (
                      <>
                        {"수집이 끝난 뒤 받아와요 — "}
                        <Link to={`/admin/crawl/${activeCrawlJob.id}`}>진행 중인 수집 보기</Link>
                      </>
                    ) : (
                      "IMSLP에서 받아오는 중이에요 — 2초 간격으로 천천히 받아요"
                    )}
                  </p>
                ) : null}

                {alert?.kind === "action-failed" ? <InlineAlert variant="danger">{ACTION_ERROR}</InlineAlert> : null}
                {alert?.kind === "fetch-error" ? <InlineAlert variant="danger">{alert.message}</InlineAlert> : null}
                {alert?.kind === "fetch-failed" ? (
                  <InlineAlert variant="danger">
                    {"파일을 받다가 끊겼어요 — "}
                    <button className="btn btn-text" type="button" onClick={() => fetchFile(edition)}>
                      다시 시도
                    </button>
                    {alert.detail ? ` ${alert.detail}` : null}
                  </InlineAlert>
                ) : null}

                {openPanelEditionId === edition.id ? (
                  <RecommendChangePanel
                    workId={workId}
                    edition={edition}
                    currentEdition={recommended}
                    currentReason={recommendation?.current ?? null}
                    onCancel={() => setOpenPanelEditionId(null)}
                    onDone={recommendDone(edition)}
                  />
                ) : null}
              </div>
            );
          })}
        </div>
      )}

      {modal ? (
        <EditionFormModal
          mode={modal.mode}
          workId={workId}
          edition={modal.edition ?? null}
          composer={composer}
          onClose={() => setModal(null)}
          onSaved={savedEdition}
        />
      ) : null}

      {confirmTarget ? (
        <ConfirmDialog
          title={
            confirmTarget.id === recommendedEditionId ? "추천이 해제됩니다. 삭제할까요?" : "이 판본을 삭제할까요?"
          }
          description={
            confirmTarget.downloadCount > 0
              ? `다운로드 기록 ${formatCount(confirmTarget.downloadCount)}건이 있는 판본이에요`
              : undefined
          }
          confirmLabel="삭제"
          cancelLabel="취소"
          danger
          onCancel={() => setConfirmTarget(null)}
          onConfirm={() => remove(confirmTarget)}
        />
      ) : null}
    </section>
  );
}
