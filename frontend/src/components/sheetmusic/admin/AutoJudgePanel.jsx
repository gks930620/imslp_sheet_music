import { useState } from "react";
import { ConfirmDialog } from "../../common/ConfirmDialog.jsx";
import { InlineAlert } from "../../common/InlineAlert.jsx";
import { callApi } from "../../../lib/http.js";
import { formatAutoJudgeSkipReason, formatCount } from "../../../lib/format.js";

/**
 * 대기함 상단 "자동 판정 실행" (02_API §5-8-1 · §5-11 · §5-12 · §7, 기획 §3 F7-6(A) · §11-1).
 *
 * 이 단계를 건너뛰면 수집 직후 판본이 전부 '확인 중'이라 **바로 받기 가능한 곡이 0개**다(기획 §F7-6 A).
 * 순서는 미리보기(dryRun) → 확인 → 실행이다. 한 번에 수천 건을 여는 버튼이라 숫자를 먼저 보여준다.
 * 되돌리기는 같은 규모를 **닫는** 동작이라 확인 모달도 같은 값으로 숫자를 먼저 말하고,
 * 결과 안내는 **되돌리지 않은 것**(추천 지정)을 반드시 말한다.
 *
 * "자동 판정만 되돌리기" 의 노출 조건은 **서버가 주는 잔량**(§5-8-1 `autoJudged.revertibleEditions`)이다.
 * 이 화면이 "내가 방금 실행했나" 를 기억하면 새로고침·재방문에서 버튼이 사라진다 — 되돌리기는
 * 수천 판본을 한 번에 공개로 여는 동작의 안전장치라 언제 들어와도 도달할 수 있어야 한다(qa 5차 결함 1).
 *
 * 그래서 로컬 기억은 **서버 값을 보조할 뿐이고 새 §5-8 응답 하나로 끝난다**(§5-8-1 2-1·2-2·2-3).
 * 방향은 일부러 비대칭이다 — `result`(실행)는 재조회 전의 짧은 창에서 진입점을 **더 보이게만** 하고,
 * `undone`(되돌리기)은 그 창에서 버튼·잔량 줄을 **감추되 새 응답에 즉시 진다**.
 * 되돌린 직후의 `autoJudged` 는 방금 되돌린 것을 세고 있는 철 지난 값이라, 그대로 두면 화면이
 * "812개를 되돌렸어요" 와 "되돌릴 수 있는 자동 판정 812개" 를 동시에 말하고 한 번 더 누르면
 * `reverted: 0` 응답이 그 안내를 덮는다(연타 대책은 진입점을 없애는 이것 하나로 끝낸다).
 * 반대로 **되돌린 적이 있다는 사실만으로 감추면 안 된다** — 그 사이 다른 관리자가 실행했을 수 있고,
 * 서버가 있다고 말하는데 화면이 감추면 결함 1(진입점 소실)이 그대로 재발한다.
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

/**
 * 되돌리기 확인 모달 본문 (02 §5-8-1 문구 표). 잔량 줄과 **같은 값**(`autoJudged`)에서 만든다 —
 * 예고가 두 벌이면 다음에 한쪽만 고쳐지고, 되돌리기는 예고가 틀리면 안 되는 동작이다.
 * 닫히는 곡이 없으면 그 절을 쓰지 않고("0곡" 금지), 셀 값이 아직 없는 실행 직후 창에서는
 * 숫자 절을 통째로 뺀다("판본 0개" 를 만들지 않는다). 유지 문장은 세 경우 모두 붙는다(기획 §11-1).
 */
function undoConfirmText(editions, works) {
  if (editions <= 0) return `자동으로 매긴 저작권 판정만 '확인 중'으로 되돌려요. ${KEPT_RECOMMENDATION}`;
  if (works > 0) {
    return `판본 ${formatCount(editions)}개를 '확인 중'으로 되돌리고 ${formatCount(
      works,
    )}곡의 다운로드가 닫혀요. ${KEPT_RECOMMENDATION}`;
  }
  return `판본 ${formatCount(editions)}개를 '확인 중'으로 되돌려요. ${KEPT_RECOMMENDATION}`;
}

function countLines(items, label, key) {
  return items.map((item) => (
    <span key={item[key]} className="auto-judge-line auto-judge-count">
      {`${label(item[key])} ${formatCount(item.count)}개`}
    </span>
  ));
}

export function AutoJudgePanel({ autoJudged, onDone }) {
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(""); // "" | "preview" | "run" | "undo"
  const [result, setResult] = useState(null);
  /**
   * 되돌리기 결과와 **그때 화면이 들고 있던 잔량 참조**를 함께 기억한다(§5-8-1 2-1).
   * 참조가 그대로면 재조회가 아직 안 온 것이고, 새 §5-8 응답이 오면 참조가 바뀌어 기억이 저절로 끝난다(2-2).
   */
  const [undone, setUndone] = useState(null); // { result, autoJudged } | null
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
      // 기억은 마지막 동작 하나뿐이다(§5-8-1 2-3) — 되돌린 직후 다시 실행하면 그 창은 끝난다
      setUndone(null);
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
      // 되돌린 뒤에는 실행 결과가 더 이상 현재 상태를 설명하지 못한다 — 결과 안내를 바꿔 단다.
      // 지금 들고 있는 잔량(autoJudged)은 방금 되돌린 것을 센 값이라 함께 적어 두고 그 창에서는 말하지 않는다.
      setUndone({ result: response.data ?? { reverted: 0, recommendationKept: 0 }, autoJudged });
      setResult(null);
      onDone?.();
    } catch {
      setError("자동 판정을 되돌리지 못했어요 — 다시 시도");
    } finally {
      setBusy("");
    }
  };

  const runLabel = busy === "preview" ? "확인하는 중…" : busy === "run" ? "실행하는 중…" : "자동 판정 실행";

  // 되돌린 직후의 창(§5-8-1 2-1) — 그 잔량은 방금 되돌린 것을 세고 있으므로 없는 것으로 본다.
  // 새 응답이 오면 참조가 달라져 이 창이 끝나고, 그 값이 0 보다 크면 버튼은 다시 보인다(2-2).
  const justUndone = Boolean(undone) && undone.autoJudged === autoJudged;
  // 서버가 아직 필드를 주지 않는 동안에도 화면이 깨지지 않게 기본값 0 으로 읽는다
  const revertibleEditions = justUndone ? 0 : (autoJudged?.revertibleEditions ?? 0);
  const revertibleWorks = justUndone ? 0 : (autoJudged?.revertibleRecommendedWorks ?? 0);
  const undoResult = undone?.result;
  const canUndo = revertibleEditions > 0 || Boolean(result);

  return (
    <section className="auto-judge">
      <div className="auto-judge-actions">
        <button className="btn btn-outline" type="button" disabled={Boolean(busy)} onClick={openPreview}>
          <span className="material-icons" aria-hidden="true">
            auto_fix_high
          </span>
          {runLabel}
        </button>
        {canUndo ? (
          <button className="btn btn-text" type="button" disabled={Boolean(busy)} onClick={() => setUndoConfirm(true)}>
            <span className="material-icons" aria-hidden="true">
              undo
            </span>
            자동 판정만 되돌리기
          </button>
        ) : null}
      </div>

      {/* 되돌릴 것이 남아 있을 때만 잔량을 말한다. 닫히는 곡이 0 이면 그 절은 쓰지 않는다("0곡" 금지) */}
      {revertibleEditions > 0 ? (
        <p className="auto-judge-remaining">
          {`되돌릴 수 있는 자동 판정 ${formatCount(revertibleEditions)}개`}
          {revertibleWorks > 0 ? ` · 되돌리면 ${formatCount(revertibleWorks)}곡의 다운로드가 닫혀요` : null}
        </p>
      ) : null}

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
          description={undoConfirmText(revertibleEditions, revertibleWorks)}
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
