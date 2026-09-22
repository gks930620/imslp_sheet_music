import { useEffect, useState } from "react";
import { InlineAlert } from "../../common/InlineAlert.jsx";
import { PreviewLightbox } from "../../common/PreviewLightbox.jsx";
import { callApi } from "../../../lib/http.js";
import {
  RECOMMEND_REASONS,
  RECOMMEND_WARNING_GROUPS,
  RECOMMEND_WARNING_ORDER,
  formatAssignedByLine,
  formatCount,
  formatEditionKind,
  formatEditionScope,
  formatRecommendReason,
  formatRecommendWarning,
  formatRecommendWarningHelp,
  recommendWarningVariant,
} from "../../../lib/format.js";

const GENERIC_FAIL_MESSAGE = "바꾸지 못했어요. 잠시 후 다시 시도해 주세요";

/** ① "지금 추천" 둘째 줄 — A-1 의 `formatAutoEvidence` 보다 짧은 전용 문구다(화면정의 06 A-3 ①) */
function compactAutoEvidence(auto) {
  if (!auto) return "";
  if (auto.candidateCount === 1) return "고를 수 있는 판본이 이것 하나뿐이었어요";
  const count = auto.imslpDownloadCount;
  const head = count === null || count === undefined ? "IMSLP 다운로드 수가 적혀 있지 않은 판본이에요" : `IMSLP ${formatCount(count)}회`;
  return `${head} · 후보 ${formatCount(auto.candidateCount)}개 중 ${auto.rank}위`;
}

/** ① "지금 추천" 값 줄 `{종류} · {포함 범위} · {출판사} / {편집자} / {연도}` */
function editionSummaryLine(edition) {
  const meta = [edition.publisher || "–", edition.editor || "–", edition.publishYear ?? "–"].join(" / ");
  return `${formatEditionKind(edition.kind)} · ${formatEditionScope(edition)} · ${meta}`;
}

/** ② 경고 묶음 상자 — 상자는 하나, 개수 1개면 한 줄만, 2개 이상이면 묶음(둘 다 있을 때만 묶음 제목) */
function WarningsBox({ warnings, edition }) {
  const orderedCodes = RECOMMEND_WARNING_ORDER.filter((code) => warnings.includes(code));
  if (orderedCodes.length === 0) return null;
  const variant = recommendWarningVariant(orderedCodes);

  if (orderedCodes.length === 1) {
    const code = orderedCodes[0];
    const help = formatRecommendWarningHelp(code, edition);
    return (
      <InlineAlert variant={variant}>
        <p className={code === "WORK_BECOMES_CLOSED" ? "recommend-warning-primary" : undefined}>
          {formatRecommendWarning(code, edition)}
        </p>
        {help ? <p className="recommend-warning-help">{help}</p> : null}
      </InlineAlert>
    );
  }

  const groups = RECOMMEND_WARNING_GROUPS.map((group) => ({
    ...group,
    codes: orderedCodes.filter((code) => group.codes.includes(code)),
  })).filter((group) => group.codes.length > 0);
  const showGroupTitles = groups.length > 1;

  return (
    <InlineAlert variant={variant}>
      <p className="recommend-warning-title">{`바꾸기 전에 확인할 것 ${orderedCodes.length}가지`}</p>
      {groups.map((group) => (
        <div key={group.key} className="recommend-warning-group">
          {showGroupTitles ? <p className="recommend-warning-group-title">{group.title}</p> : null}
          <ul>
            {group.codes.map((code) => {
              const help = formatRecommendWarningHelp(code, edition);
              return (
                <li key={code} className={code === "WORK_BECOMES_CLOSED" ? "recommend-warning-primary" : undefined}>
                  {formatRecommendWarning(code, edition)}
                  {help ? <span className="recommend-warning-help"> {help}</span> : null}
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </InlineAlert>
  );
}

/**
 * 화면 A-3 "이 판본으로 바꾸기" 패널 — 화면정의 `06_관리자_판본관리.md` A-3, 계약 02 §5-6-2 · §5-6.
 *
 * props: workId, edition(바꿀 판본), currentEdition(지금 추천 | null), currentReason(§4-7-2 current | null),
 *        onCancel(), onDone(§5-6 응답)
 */
export function RecommendChangePanel({ workId, edition, currentEdition, currentReason, onCancel, onDone }) {
  const [warnings, setWarnings] = useState([]);
  const [selectedReason, setSelectedReason] = useState(null);
  const [note, setNote] = useState("");
  const [reviewedChecked, setReviewedChecked] = useState(false);
  const [reasonError, setReasonError] = useState("");
  const [noteError, setNoteError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [saving, setSaving] = useState(false);
  const [lightboxOpen, setLightboxOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ editionId: String(edition.id) });
    callApi(`/api/admin/works/${workId}/recommended-edition/preview?${params.toString()}`)
      .then((result) => {
        if (!cancelled) setWarnings(result.data?.warnings ?? []);
      })
      .catch(() => {
        if (!cancelled) setWarnings([]); // 예고는 안내다 — 못 받아도 패널은 그대로 연다
      });
    return () => {
      cancelled = true;
    };
  }, [workId, edition.id]);

  const reasons = RECOMMEND_REASONS.filter((reason) => !(reason.needsPrevious && !currentEdition));

  const submit = async () => {
    if (!selectedReason) {
      setReasonError("왜 이 판본을 골랐는지 골라 주세요");
      return;
    }
    const trimmedNote = note.trim();
    if (selectedReason === "OTHER" && !trimmedNote) {
      setNoteError("왜 이 판본을 골랐는지 적어 주세요");
      return;
    }
    setReasonError("");
    setNoteError("");
    setSubmitError("");
    setSaving(true);
    try {
      const result = await callApi(`/api/admin/works/${workId}/recommended-edition`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          editionId: edition.id,
          reason: selectedReason,
          note: trimmedNote ? trimmedNote : null,
          reviewed: reviewedChecked,
        }),
      });
      onDone?.(result.data);
    } catch (error) {
      setSubmitError(error?.status === 400 ? error.message : GENERIC_FAIL_MESSAGE);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="recommend-change-panel">
      <p className="recommend-change-title">이 판본으로 바꾸기</p>

      <div className="recommend-change-current">
        <span className="recommend-change-current-label">지금 추천</span>
        {!currentEdition ? (
          <span className="recommend-change-current-value">지금 추천 없음 — 이 곡의 첫 추천이에요</span>
        ) : (
          <>
            <span className="recommend-change-current-value">{editionSummaryLine(currentEdition)}</span>
            <span className="recommend-change-current-reason">
              {!currentReason
                ? "기록이 없어요 — 왜 이 판본이었는지 남아 있지 않아요"
                : currentReason.source === "AUTO"
                  ? `${formatAssignedByLine(currentReason)} · ${compactAutoEvidence(currentReason.auto)}`
                  : `${formatAssignedByLine(currentReason)} · ${formatRecommendReason(currentReason.reason)}`}
            </span>
          </>
        )}
      </div>

      <WarningsBox warnings={warnings} edition={edition} />

      <fieldset className="form-group recommend-reason-group">
        <legend className="form-label required">왜 이 판본을 고르셨어요?</legend>
        {reasons.map((reason) => {
          const inputId = `recommendReasonOption-${reason.code}`;
          const helpId = reason.help ? `${inputId}-help` : undefined;
          return (
            <div key={reason.code} className="radio-item recommend-reason-item">
              <input
                type="radio"
                id={inputId}
                name="recommendReason"
                value={reason.code}
                checked={selectedReason === reason.code}
                aria-describedby={helpId}
                onChange={() => {
                  setSelectedReason(reason.code);
                  setReasonError("");
                }}
              />
              {/* 라벨은 라디오 이름만 갖는다 — 보조 줄(help)까지 라벨에 넣으면 "메모"가 들어간 보조 줄이
                  getByLabelText(/메모/) 로 실제 메모 칸과 함께 걸린다(6번 "기타" 항목의 보조 줄) */}
              <label htmlFor={inputId} className="recommend-reason-label">
                {reason.label}
              </label>
              {reason.help ? (
                <span id={helpId} className="form-help">
                  {reason.help}
                </span>
              ) : null}
            </div>
          );
        })}
        {reasonError ? <span className="form-error">{reasonError}</span> : null}
      </fieldset>

      <div className="form-group">
        <label
          className={`form-label${selectedReason === "OTHER" ? " required" : ""}`}
          htmlFor="recommendChangeNote"
        >
          {selectedReason === "OTHER" ? "메모" : "메모 (선택)"}
        </label>
        <input
          id="recommendChangeNote"
          className={`form-input${noteError ? " form-input-error" : ""}`}
          placeholder="예: 앞 추천은 관현악 총보였음"
          maxLength={300}
          value={note}
          onChange={(event) => {
            setNote(event.target.value);
            setNoteError("");
          }}
        />
        <span className="recommend-note-counter">{`${note.length}/300`}</span>
        {noteError ? <span className="form-error">{noteError}</span> : null}
      </div>

      <div className="form-group recommend-change-confirm">
        <label className="radio-item recommend-confirm-checkbox">
          <input
            type="checkbox"
            checked={reviewedChecked}
            onChange={(event) => setReviewedChecked(event.target.checked)}
          />
          <span>미리보기를 열어 이 곡의 피아노 악보가 맞는지 확인했어요</span>
        </label>
        <button type="button" className="btn btn-text" disabled={!edition.previewUrl} onClick={() => setLightboxOpen(true)}>
          미리보기 열기
        </button>
        {!edition.previewUrl ? (
          <span className="form-help">미리보기가 아직 없어요 — PDF를 직접 확인해 주세요</span>
        ) : null}
        <span className="form-help">
          {reviewedChecked
            ? "이 곡은 '추천 판본 확인 필요'에서 바로 빠져요"
            : "체크하지 않으면 이 곡은 '추천 판본 확인 필요'에 남아요"}
        </span>
      </div>

      {submitError ? <InlineAlert variant="danger">{submitError}</InlineAlert> : null}

      <div className="form-actions recommend-change-actions">
        <button type="button" className="btn btn-secondary" disabled={saving} onClick={() => onCancel?.()}>
          취소
        </button>
        <button type="button" className="btn btn-primary" disabled={saving} onClick={submit}>
          {saving ? "바꾸는 중…" : "이 판본으로 바꾸기"}
        </button>
      </div>

      {lightboxOpen && edition.previewUrl ? (
        <PreviewLightbox
          src={edition.previewUrl}
          caption={`${formatEditionKind(edition.kind)} · ${formatEditionScope(edition)} (1쪽/${edition.pageCount ?? "?"}쪽)`}
          onClose={() => setLightboxOpen(false)}
        />
      ) : null}
    </div>
  );
}
