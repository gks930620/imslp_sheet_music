import { useState } from "react";
import { ConfirmDialog } from "../../common/ConfirmDialog.jsx";
import { InlineAlert } from "../../common/InlineAlert.jsx";
import { callApi } from "../../../lib/http.js";
import { formatAutoJudgeSkipReason, formatCount } from "../../../lib/format.js";

/**
 * 대기함 상단 "자동 판정 실행" (02_API §5-11 · §5-12 · §7, 기획 §3 F7-6(A) · §11-1).
 *
 * 순서는 미리보기(dryRun) → 확인 → 실행이다. 한 번에 수천 건을 여는 버튼이라 숫자를 먼저 보여준다.
 * 실행 뒤에는 "자동 판정만 되돌리기" 가 보이고, 되돌리기 결과는 **되돌리지 않은 것**(추천 지정)을 반드시 말한다.
 */
const RULE_LABELS = {
  CC_REDISTRIBUTABLE: "재배포 허용 라이선스(CC)",
  PD_NO_EDITOR: "퍼블릭 도메인 · 편집자 표기 없음",
  PD_OLD_PUBLICATION: "퍼블릭 도메인 · 출판 120년 경과",
};

/** 규칙 문구는 이 모달에만 나오므로 여기 둔다. 사유 문구는 대기함 행과 함께 쓰므로 lib/format.js 가 갖는다. */
function ruleLabel(rule) {
  return RULE_LABELS[rule] ?? rule;
}

const AUTO_JUDGE_URL = "/api/admin/copyright/auto-judge";
const KEPT_RECOMMENDATION = "자동으로 지정된 추천 판본은 그대로 있어요 — 바꾸려면 곡 편집에서 해제하세요.";

function countLines(items, label, key) {
  return items.map((item) => (
    <span key={item[key]} className="auto-judge-line auto-judge-count">
      {`${label(item[key])} ${formatCount(item.count)}개`}
    </span>
  ));
}

export function AutoJudgePanel({ onDone }) {
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(""); // "" | "preview" | "run" | "undo"
  const [result, setResult] = useState(null);
  const [undoResult, setUndoResult] = useState(null);
  const [undoConfirm, setUndoConfirm] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const call = async (dryRun) => {
    const response = await callApi(AUTO_JUDGE_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ dryRun, assignRecommended: true }),
    });
    return response.data;
  };

  const openPreview = async () => {
    setBusy("preview");
    setError("");
    setNotice("");
    try {
      const data = await call(true);
      // 멱등 실행이라 두 번째부터는 열 것이 없다 — 그때는 확인 창 대신 그 사실만 말한다(02 §5-11)
      if ((data?.judgedFree ?? 0) === 0) {
        setNotice(
          `지금 자동으로 열 수 있는 판본이 없어요 — 확인 중 ${formatCount(data?.remainingUnknown ?? 0)}개는 사람이 봐야 해요`,
        );
      } else {
        setPreview(data);
      }
    } catch {
      setError("자동 판정을 실행하지 못했어요 — 다시 시도");
    } finally {
      setBusy("");
    }
  };

  const run = async () => {
    setPreview(null);
    setBusy("run");
    setError("");
    try {
      const data = await call(false);
      setResult(data);
      setUndoResult(null);
      onDone?.();
    } catch {
      setError("자동 판정을 실행하지 못했어요 — 다시 시도");
    } finally {
      setBusy("");
    }
  };

  const undo = async () => {
    setUndoConfirm(false);
    setBusy("undo");
    setError("");
    try {
      const response = await callApi(`${AUTO_JUDGE_URL}/undo`, { method: "POST" });
      // 되돌린 뒤에는 실행 결과가 더 이상 현재 상태를 설명하지 못한다 — 결과 안내를 바꿔 단다
      setUndoResult(response.data ?? { reverted: 0, recommendationKept: 0 });
      setResult(null);
      onDone?.();
    } catch {
      setError("자동 판정을 되돌리지 못했어요 — 다시 시도");
    } finally {
      setBusy("");
    }
  };

  const runLabel = busy === "preview" ? "확인하는 중…" : busy === "run" ? "실행하는 중…" : "자동 판정 실행";

  return (
    <section className="auto-judge">
      <div className="auto-judge-actions">
        <button className="btn btn-outline" type="button" disabled={Boolean(busy)} onClick={openPreview}>
          <span className="material-icons" aria-hidden="true">
            auto_fix_high
          </span>
          {runLabel}
        </button>
        {result ? (
          <button className="btn btn-text" type="button" disabled={Boolean(busy)} onClick={() => setUndoConfirm(true)}>
            <span className="material-icons" aria-hidden="true">
              undo
            </span>
            자동 판정만 되돌리기
          </button>
        ) : null}
      </div>

      {error ? <InlineAlert variant="danger">{error}</InlineAlert> : null}

      {notice ? <InlineAlert variant="info">{notice}</InlineAlert> : null}

      {result ? (
        <InlineAlert variant="success">
          <p>
            {`판본 ${formatCount(result.judgedFree)}개를 '자유 이용 가능'으로 판정했어요 · 추천 판본 ${formatCount(
              result.recommendedAssigned,
            )}곡 자동 지정 · 확인 중 ${formatCount(result.remainingUnknown)}개 남음`}
          </p>
          {result.recommendedAssigned > 0 ? (
            <p>자동으로 지정된 추천 판본은 아직 미검수예요 — 관리 홈의 &apos;추천 판본 확인 필요&apos;에서 확인해 주세요</p>
          ) : null}
        </InlineAlert>
      ) : null}

      {undoResult ? (
        <InlineAlert variant="warning">
          <p>
            {undoResult.reverted > 0
              ? `저작권 판정 ${formatCount(undoResult.reverted)}개를 되돌렸어요. ${KEPT_RECOMMENDATION}`
              : `되돌릴 자동 판정이 없었어요. ${KEPT_RECOMMENDATION}`}
          </p>
          {undoResult.recommendationKept > 0 ? (
            <p>{`${formatCount(undoResult.recommendationKept)}곡은 다시 판정해야 다운로드가 열려요`}</p>
          ) : null}
        </InlineAlert>
      ) : null}

      {preview ? (
        <ConfirmDialog
          title={`판본 ${formatCount(preview.judgedFree)}개를 '자유 이용 가능'으로 열까요?`}
          description={
            <>
              <span className="auto-judge-line">
                {`확인 중인 판본 ${formatCount(preview.targetCount)}개 중 ${formatCount(
                  preview.judgedFree,
                )}개가 자동 규칙에 맞아요.`}
              </span>
              <span className="auto-judge-line">
                {`추천 판본이 없는 곡 ${formatCount(preview.recommendedAssigned)}곡에 추천이 함께 지정돼요 (미검수).`}
              </span>
              <span className="auto-judge-line">
                {`나머지 ${formatCount(preview.remainingUnknown)}개는 '확인 중'으로 남아요.`}
              </span>
              <span className="auto-judge-line auto-judge-sub">규칙별</span>
              {countLines(preview.byRule ?? [], ruleLabel, "rule")}
              <span className="auto-judge-line auto-judge-sub">남는 이유</span>
              {countLines(preview.skipped ?? [], formatAutoJudgeSkipReason, "reason")}
            </>
          }
          confirmLabel="실행"
          cancelLabel="취소"
          onCancel={() => setPreview(null)}
          onConfirm={run}
        />
      ) : null}

      {undoConfirm ? (
        <ConfirmDialog
          title="자동 판정을 되돌릴까요?"
          description={`자동으로 매긴 저작권 판정만 '확인 중'으로 되돌려요. ${KEPT_RECOMMENDATION}`}
          confirmLabel="되돌리기"
          cancelLabel="취소"
          danger
          onCancel={() => setUndoConfirm(false)}
          onConfirm={undo}
        />
      ) : null}
    </section>
  );
}
