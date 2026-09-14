import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CopyrightPendingPage } from "./CopyrightPendingPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { adminComposers, pageResponse, pendingCopyrightResponse } from "../../test/fixtures.js";
import { expectText, findText } from "../../test/text.js";

/**
 * 되돌리기 **확인 모달의 숫자** — 02_API_명세서 §5-8-1 문구 표 (2026-09-09 신설, senior-dev. **Red**).
 *
 * 같은 패널의 실행 경로는 `dryRun` 미리보기 모달로 **"수천 건을 여는 버튼이라 숫자를 먼저 보여준다"** 는 원칙을
 * 이미 지킨다. 되돌리기는 그 반대 방향으로 같은 규모를 **닫는** 동작인데(수백~수천 판본의 다운로드가 그 순간 막힌다)
 * 확인 모달이 문장뿐이라 관리자는 **무엇이 얼마나 닫히는지 모른 채** 확인을 누른다. 기준을 맞춘다.
 *
 * 숫자의 출처는 §5-8-1 `autoJudged` 하나다 — 잔량 줄과 모달이 **같은 값**을 말해야 한다.
 * 예고가 두 벌이면 다음에 한쪽만 고쳐지고, 되돌리기는 예고가 틀리면 안 되는 동작이다(§5-8-1 불변식).
 *
 * "0곡" 을 만들지 않는 규칙은 잔량 줄(§5-8-1 화면 계약 3)·§5-12 결과 안내와 같다 —
 * 닫힐 것이 없다는 말을 굳이 하지 않는다. 서버 잔량이 아직 `0` 인 **실행 직후 창**에서는 셀 수가 없으므로
 * 숫자 절을 통째로 빼고 기존 문장만 쓴다("판본 0개" 를 만들지 않는다).
 */
const PENDING = /\/api\/admin\/copyright\/pending/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const AUTO_JUDGE = /\/api\/admin\/copyright\/auto-judge$/;
const UNDO = /\/api\/admin\/copyright\/auto-judge\/undo$/;

const UNDO_BUTTON = { name: /자동 판정만 되돌리기/ };
const KEPT = "자동으로 지정된 추천 판본은 그대로 있어요";

const RUN_RESULT = {
  dryRun: false,
  targetCount: 1792,
  judgedFree: 812,
  remainingUnknown: 980,
  recommendedAssigned: 17,
  byRule: [
    { rule: "CC_REDISTRIBUTABLE", count: 12 },
    { rule: "PD_NO_EDITOR", count: 300 },
    { rule: "PD_OLD_PUBLICATION", count: 500 },
  ],
  skipped: [
    { reason: "LICENSE_NOT_REDISTRIBUTABLE", count: 400 },
    { reason: "COMPOSER_DEATH_YEAR_UNKNOWN", count: 20 },
    { reason: "COMPOSER_COPYRIGHT_ACTIVE", count: 5 },
    { reason: "PUBLICATION_TOO_RECENT", count: 55 },
    { reason: "EDITOR_UNVERIFIABLE", count: 500 },
  ],
};

function renderPage(autoJudged = { revertibleEditions: 812, revertibleRecommendedWorks: 17 }) {
  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: AUTO_JUDGE, method: "POST", data: RUN_RESULT },
    { url: UNDO, method: "POST", data: { reverted: 812, recommendationKept: 17 } },
    { url: PENDING, method: "GET", data: pendingCopyrightResponse({ autoJudged }) },
  ]);
  return renderWithProviders(<CopyrightPendingPage />, {
    route: "/admin/copyright",
    path: "/admin/copyright",
    auth: "admin",
  });
}

/** 모달 안의 말만 본다 — 잔량 줄이 뒤에 그대로 있어 body 전체로 보면 어느 쪽이 말했는지 구분되지 않는다. */
function dialogText() {
  return (screen.getByRole("dialog").textContent ?? "").replace(/\s+/g, " ");
}

async function openUndoDialog(user) {
  await user.click(screen.getByRole("button", UNDO_BUTTON));
  await screen.findByRole("dialog");
}

describe("대기함 — 되돌리기 확인 모달의 숫자 (02 §5-8-1 문구 표)", () => {
  it("무엇이 얼마나 되돌아가고 몇 곡이 닫히는지 확인 전에 말한다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("저작권 판정 대기함");

    await openUndoDialog(user);

    expect(dialogText()).toContain("판본 812개를 '확인 중'으로 되돌리고 17곡의 다운로드가 닫혀요");
    expect(dialogText()).toContain(KEPT);
  });

  it("닫히는 곡이 없으면 그 절을 쓰지 않는다 — '0곡' 을 만들지 않는다", async () => {
    const user = userEvent.setup();
    renderPage({ revertibleEditions: 812, revertibleRecommendedWorks: 0 });
    await findText("저작권 판정 대기함");

    await openUndoDialog(user);

    expect(dialogText()).toContain("판본 812개를 '확인 중'으로 되돌려요");
    expect(dialogText()).not.toContain("0곡");
    expect(dialogText()).not.toContain("다운로드가 닫혀요");
    expect(dialogText()).toContain(KEPT);
  });

  it("잔량 줄과 모달이 같은 값을 말한다 — 예고가 두 벌이면 다음에 한쪽만 고쳐진다", async () => {
    const user = userEvent.setup();
    renderPage({ revertibleEditions: 1234, revertibleRecommendedWorks: 56 });
    await findText("되돌릴 수 있는 자동 판정 1,234개");
    expectText("되돌리면 56곡의 다운로드가 닫혀요");

    await openUndoDialog(user);

    expect(dialogText()).toContain("판본 1,234개를 '확인 중'으로 되돌리고 56곡의 다운로드가 닫혀요");
  });

  it("셀 값이 아직 없는 실행 직후 창에서는 숫자 절을 통째로 뺀다 — '판본 0개' 를 만들지 않는다", async () => {
    const user = userEvent.setup();
    renderPage({ revertibleEditions: 0, revertibleRecommendedWorks: 0 });
    await findText("저작권 판정 대기함");
    await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));
    await screen.findByRole("dialog");
    await user.click(screen.getByRole("button", { name: "실행" }));
    await findText("판정했어요");

    await openUndoDialog(user);

    expect(dialogText()).toContain("자동으로 매긴 저작권 판정만 '확인 중'으로 되돌려요");
    expect(dialogText()).not.toContain("판본 0개");
    expect(dialogText()).toContain(KEPT);
  });
});
