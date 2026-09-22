import { useState } from "react";
import { InlineAlert } from "../../common/InlineAlert.jsx";
import { PreviewLightbox } from "../../common/PreviewLightbox.jsx";
import { callApi } from "../../../lib/http.js";
import {
  formatAdminDateTime,
  formatAssignedByLine,
  formatAutoEvidence,
  formatAutoRule,
  formatClearedReason,
  formatCount,
  formatDecidedBy,
  formatEditionImprint,
  formatEditionKind,
  formatEditionScope,
  formatEditionSnapshot,
  formatRecommendReason,
} from "../../../lib/format.js";

const REVIEW_FAIL_MESSAGE = "처리하지 못했어요. 잠시 후 다시 시도해 주세요";
/** 2건 이상일 때만 접힘 헤더를 만든다 — 1건이면 그 줄이 이미 위에 크게 보이고 있다(화면정의 06 A-1) */
const HISTORY_COLLAPSE_MIN = 2;

/** 이력 한 줄의 "사유" 자리 — 지운 줄은 뺀 사유, 자동 줄은 규칙 요약, 사람 줄은 고른 사유 라벨 */
function historyReasonText(item) {
  if (item.action === "CLEARED") return formatClearedReason(item.clearedReason);
  if (item.source === "AUTO") {
    const auto = item.auto;
    if (!auto) return "";
    return auto.candidateCount === 1
      ? "자동으로 골랐어요 · 고를 수 있는 판본이 이것 하나뿐이었어요"
      : `자동으로 골랐어요 · 후보 ${formatCount(auto.candidateCount)}개 중 ${auto.rank}위`;
  }
  return formatRecommendReason(item.reason);
}

function HistoryRow({ item, isCurrent }) {
  const prevLabel = item.previousEdition ? formatEditionImprint(item.previousEdition) : "(처음 지정)";
  const newLabel = item.action === "CLEARED" ? "추천 없음" : formatEditionImprint(item.edition);
  const reasonLabel = historyReasonText(item);
  return (
    <li className="recommendation-history-row">
      <p className="recommendation-history-meta">
        {isCurrent ? <span className="recommendation-history-pill">지금</span> : null}
        {`${formatAdminDateTime(item.decidedAt)} · ${formatDecidedBy(item)}`}
      </p>
      <p className="recommendation-history-editions">{`${prevLabel} → ${newLabel}`}</p>
      {reasonLabel ? <p className="recommendation-history-reason">{reasonLabel}</p> : null}
      {item.note ? <p className="recommendation-history-note">{item.note}</p> : null}
    </li>
  );
}

/** ▸ 바뀐 이력 (N) 접힘 — §4-7-1 "더 보기" 로 최대 200줄을 같은 자리에서 펼친다 */
function HistoryDisclosure({ workId, historyCount, history, hasMore }) {
  const [expanded, setExpanded] = useState(false);
  const [items, setItems] = useState(history);
  const [more, setMore] = useState(hasMore);
  const [loading, setLoading] = useState(false);

  const loadMore = async () => {
    setLoading(true);
    try {
      const result = await callApi(`/api/admin/works/${workId}/recommendation-history`);
      setItems(result.data?.history ?? items);
      setMore(false);
    } catch {
      /* 더 보기 실패는 조용히 둔다 — 이미 보이는 줄은 그대로 남는다 */
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="recommendation-history">
      <button
        type="button"
        className="btn btn-text recommendation-history-toggle"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
      >
        {`${expanded ? "▾" : "▸"} 바뀐 이력 (${historyCount})`}
      </button>
      {expanded ? (
        <ul className="recommendation-history-list">
          {items.map((item, index) => (
            <HistoryRow key={item.id} item={item} isCurrent={index === 0} />
          ))}
        </ul>
      ) : null}
      {expanded && more ? (
        <button type="button" className="btn btn-text recommendation-history-more" onClick={loadMore} disabled={loading}>
          더 보기
        </button>
      ) : null}
    </div>
  );
}

/**
 * 화면 A-1 "이 판본을 고른 이유" — 화면정의 `06_관리자_판본관리.md` A-1, 계약 02 §4-7-2 · §4-7-1 · §5-6-1.
 *
 * props: workId, hasRecommendation, reviewed, recommendation(§4-7-2 | null=불러오지 못함),
 *        recommendedEdition(미리보기용 | null), onChanged, onToast
 */
export function RecommendationReasonBox({
  workId,
  hasRecommendation,
  reviewed,
  recommendation,
  recommendedEdition,
  onChanged,
  onToast,
}) {
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewFailed, setReviewFailed] = useState(false);

  const toggleReview = async (nextReviewed) => {
    setReviewFailed(false);
    setReviewBusy(true);
    try {
      await callApi(`/api/admin/works/${workId}/recommended-edition/review`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reviewed: nextReviewed }),
      });
      onToast?.(nextReviewed ? "확인했어요" : "확인을 해제했어요");
      onChanged?.();
    } catch {
      setReviewFailed(true);
    } finally {
      setReviewBusy(false);
    }
  };

  // 추천이 없는 곡: 상자 자체가 없다. 다만 이력이 남아 있으면 접힘 줄만 남는다(화면정의 06 A-0 · 8-B 7).
  if (!hasRecommendation) {
    const historyCount = recommendation?.historyCount ?? 0;
    if (historyCount < 1) return null;
    return (
      <div className="recommendation-reason-box recommendation-reason-box-history-only">
        <HistoryDisclosure
          workId={workId}
          historyCount={historyCount}
          history={recommendation.history}
          hasMore={recommendation.hasMore}
        />
      </div>
    );
  }

  // 근거를 못 받았을 때 — 판본 목록은 막지 않고 이 자리만 한 줄로 알린다(화면정의 06 A-2 상태별 UI)
  if (!recommendation) {
    return (
      <div className="recommendation-reason-box panel">
        <p className="recommendation-reason-error">고른 이유를 불러오지 못했어요</p>
        <button type="button" className="btn btn-text" onClick={() => onChanged?.()}>
          다시 시도
        </button>
      </div>
    );
  }

  const { current, historyCount, history, hasMore } = recommendation;
  const hasPreview = Boolean(recommendedEdition?.previewUrl);

  return (
    <div className="recommendation-reason-box panel">
      <p className="recommendation-reason-title">이 판본을 고른 이유</p>

      {!current ? (
        <p className="recommendation-reason-empty">
          <strong>기록이 없어요</strong> — 근거를 남기는 기능이 생기기 전에 정해졌어요
        </p>
      ) : current.source === "AUTO" ? (
        <>
          <p className="recommendation-reason-who">
            {`${formatAssignedByLine(current)} · ${formatAdminDateTime(current.decidedAt)}`}
          </p>
          <p className="recommendation-reason-rule">{formatAutoRule(current.auto?.rule)}</p>
          <p className="recommendation-reason-evidence">{formatAutoEvidence(current.auto)}</p>
        </>
      ) : (
        <>
          <p className="recommendation-reason-who">
            {`${formatAssignedByLine(current)} · ${formatAdminDateTime(current.decidedAt)}`}
          </p>
          <p className="recommendation-reason-label">{formatRecommendReason(current.reason)}</p>
          {current.note ? <p className="recommendation-reason-note">{current.note}</p> : null}
          <p className="recommendation-reason-previous">
            {current.previousEdition
              ? `이전 추천: ${formatEditionSnapshot(current.previousEdition)}`
              : "처음 지정한 추천이에요"}
          </p>
        </>
      )}

      <div className="recommendation-review-row">
        {reviewed ? (
          <>
            <p className="recommendation-review-done">
              <span className="material-icons" aria-hidden="true">
                check_circle
              </span>
              미리보기를 확인한 추천이에요
            </p>
            <button type="button" className="btn btn-text" disabled={reviewBusy} onClick={() => toggleReview(false)}>
              확인 해제
            </button>
          </>
        ) : (
          <>
            <p className="recommendation-review-pending">
              <span className="material-icons" aria-hidden="true">
                warning
              </span>
              아직 아무도 미리보기를 확인하지 않았어요
            </p>
            <button
              type="button"
              className="btn btn-text"
              disabled={!hasPreview}
              onClick={() => setLightboxOpen(true)}
            >
              미리보기 열기
            </button>
            {!hasPreview ? (
              <span className="form-help">미리보기가 아직 없어요 — PDF를 직접 확인해 주세요</span>
            ) : null}
            <button type="button" className="btn btn-text" disabled={reviewBusy} onClick={() => toggleReview(true)}>
              확인함
            </button>
          </>
        )}
      </div>
      {reviewFailed ? <InlineAlert variant="danger">{REVIEW_FAIL_MESSAGE}</InlineAlert> : null}

      {!current ? (
        <p className="recommendation-reason-hint">
          {reviewed
            ? "이미 미리보기를 확인한 곡이에요. 다시 지정하면 그때부터 고른 이유가 남아요."
            : "미리보기를 열어 확인하고, 그대로 둘 거면 '확인함'을 눌러 주세요. 다른 판본이 맞으면 아래 목록에서 다시 골라 주세요."}
        </p>
      ) : null}

      {historyCount >= HISTORY_COLLAPSE_MIN ? (
        <HistoryDisclosure workId={workId} historyCount={historyCount} history={history} hasMore={hasMore} />
      ) : null}

      {lightboxOpen && hasPreview ? (
        <PreviewLightbox
          src={recommendedEdition.previewUrl}
          caption={`${formatEditionKind(recommendedEdition.kind)} · ${formatEditionScope(recommendedEdition)} (1쪽/${recommendedEdition.pageCount ?? "?"}쪽)`}
          onClose={() => setLightboxOpen(false)}
        />
      ) : null}
    </div>
  );
}
