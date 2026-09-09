import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CopyrightPendingPage } from "./CopyrightPendingPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../test/apiMock.js";
import { adminComposers, pageResponse, pendingCopyrightResponse } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

/**
 * 자동 판정 실행·되돌리기 — 02_API_명세서 §5-11 · §5-12 · §7, 기획 §3 F7-6(A) · §11-1 결정 4
 * (frontend-dev 작성. API 는 있는데 화면에 호출이 하나도 없어 관리자가 쓸 수 없었다 — qa 4차 결함).
 *
 * <p>이 단계를 건너뛰면 수집 직후 판본이 전부 "확인 중"이라 <b>바로 받기 가능한 곡이 0개</b>다.
 * 그래서 대기함 맨 위에 있어야 하고, 1,792건을 한 번에 여는 버튼이라 <b>미리보기(dryRun) → 확인 → 실행</b> 순서다.
 *
 * <p>되돌리기는 이름이 "자동 판정만 되돌리기"다. 판정만 되돌리고 <b>추천 지정은 남긴다</b> —
 * 그 사실을 결과 안내가 반드시 말해야 한다(기획 §11-1: "이름과 행동이 다르면 관리자가 멈춘다").
 */
const PENDING = /\/api\/admin\/copyright\/pending/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const AUTO_JUDGE = /\/api\/admin\/copyright\/auto-judge$/;
const UNDO = /\/api\/admin\/copyright\/auto-judge\/undo$/;

const RESULT = {
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

function renderPage(routes = []) {
  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: PENDING, method: "GET", data: pendingCopyrightResponse() },
    { url: AUTO_JUDGE, method: "POST", data: RESULT },
    { url: UNDO, method: "POST", data: { reverted: 812, recommendationKept: 17 } },
    ...routes,
  ]);
  return renderWithProviders(<CopyrightPendingPage />, {
    route: "/admin/copyright",
    path: "/admin/copyright",
    auth: "admin",
  });
}

function autoJudgeCalls() {
  return findCalls(AUTO_JUDGE).filter((call) => call.method === "POST");
}

async function openPreview(user) {
  await findText("저작권 판정 대기함");
  await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));
  await screen.findByRole("dialog");
}

async function run(user) {
  await openPreview(user);
  await user.click(screen.getByRole("button", { name: "실행" }));
  await findText("판정했어요");
}

describe("CopyrightPendingPage — 자동 판정 실행 (02 §5-11)", () => {
  it("버튼을 누르면 먼저 dryRun 으로 물어본다 — 확인 전에는 아무것도 저장하지 않는다", async () => {
    const user = userEvent.setup();
    renderPage();
    await openPreview(user);

    expect(autoJudgeCalls()).toHaveLength(1);
    expect(autoJudgeCalls()[0].body).toEqual({ dryRun: true, assignRecommended: true });
  });

  it("미리보기 숫자를 확인 창에 그대로 보여준다 (대상·열림·추천 지정·남는 것)", async () => {
    const user = userEvent.setup();
    renderPage();
    await openPreview(user);

    expectText("812");
    expectText("1,792");
    expectText("17");
    expectText("980");
  });

  it("취소하면 실행하지 않는다", async () => {
    const user = userEvent.setup();
    renderPage();
    await openPreview(user);
    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(autoJudgeCalls()).toHaveLength(1);
    expect(autoJudgeCalls().every((call) => call.body.dryRun === true)).toBe(true);
  });

  it("실행하면 dryRun: false 로 한 번 더 부르고, 결과 숫자를 화면에 남긴다", async () => {
    const user = userEvent.setup();
    renderPage();
    await run(user);

    expect(autoJudgeCalls()).toHaveLength(2);
    expect(autoJudgeCalls()[1].body).toEqual({ dryRun: false, assignRecommended: true });
    expectText("812");
    expectText("17");
  });

  it("실행 뒤 대기 목록을 다시 불러온다 — 열린 판본은 대기함에서 빠져야 한다", async () => {
    const user = userEvent.setup();
    renderPage();
    const before = findCalls(PENDING).length;
    await run(user);

    await waitFor(() => expect(findCalls(PENDING).length).toBeGreaterThan(before));
  });

  it("자동으로 열 수 있는 판본이 없으면(재실행) 확인 창 대신 그 사실만 알린다", async () => {
    const user = userEvent.setup();
    renderPage([
      {
        url: AUTO_JUDGE,
        method: "POST",
        data: { ...RESULT, dryRun: true, targetCount: 980, judgedFree: 0, remainingUnknown: 980, recommendedAssigned: 0 },
      },
    ]);
    await findText("저작권 판정 대기함");
    await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));

    await findText("자동으로 열 수 있는 판본이 없어요");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(autoJudgeCalls()).toHaveLength(1);
  });

  it("실행이 실패하면 안내하고 되돌리기 버튼을 만들지 않는다", async () => {
    const user = userEvent.setup();
    renderPage([{ url: AUTO_JUDGE, method: "POST", status: 500, error: "INTERNAL_ERROR" }]);
    await findText("저작권 판정 대기함");
    await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));

    await findText("자동 판정을 실행하지 못했어요");
    expect(screen.queryByRole("button", { name: /자동 판정만 되돌리기/ })).not.toBeInTheDocument();
  });
});

describe("CopyrightPendingPage — 자동 판정만 되돌리기 (02 §5-12, 기획 §11-1)", () => {
  it("실행 전에는 되돌리기 버튼이 없다", async () => {
    renderPage();
    await findText("저작권 판정 대기함");

    expect(screen.queryByRole("button", { name: /자동 판정만 되돌리기/ })).not.toBeInTheDocument();
  });

  it("실행한 뒤에는 '자동 판정만 되돌리기' 버튼이 있다 — 이름이 범위를 말한다", async () => {
    const user = userEvent.setup();
    renderPage();
    await run(user);

    expect(screen.getByRole("button", { name: /자동 판정만 되돌리기/ })).toBeInTheDocument();
  });

  it("되돌리기도 확인을 한 번 받고, 본문 없이 POST 한다", async () => {
    const user = userEvent.setup();
    renderPage();
    await run(user);
    await user.click(screen.getByRole("button", { name: /자동 판정만 되돌리기/ }));
    await screen.findByRole("dialog");
    await user.click(screen.getByRole("button", { name: "되돌리기" }));

    await waitFor(() => expect(findCalls(UNDO)).toHaveLength(1));
    expect(findCalls(UNDO)[0].body).toBeUndefined();
  });

  it("되돌리기 결과는 되돌린 수와 함께 '추천 판본은 그대로 있어요' 를 반드시 말한다", async () => {
    const user = userEvent.setup();
    renderPage();
    await run(user);
    await user.click(screen.getByRole("button", { name: /자동 판정만 되돌리기/ }));
    await user.click(await screen.findByRole("button", { name: "되돌리기" }));

    await findText("자동으로 지정된 추천 판본은 그대로 있어요");
    expectText("812");
    // recommendationKept — 이번 되돌리기로 다운로드가 닫힌 곡 수(§5-12)
    expectText("17곡");
  });

  it("되돌릴 것이 없으면(0건) 숫자 안내 없이도 같은 문장을 말한다", async () => {
    const user = userEvent.setup();
    renderPage([{ url: UNDO, method: "POST", data: { reverted: 0, recommendationKept: 0 } }]);
    await run(user);
    await user.click(screen.getByRole("button", { name: /자동 판정만 되돌리기/ }));
    await user.click(await screen.findByRole("button", { name: "되돌리기" }));

    await findText("자동으로 지정된 추천 판본은 그대로 있어요");
    expectNoText("17곡");
  });

  it("되돌린 뒤에도 대기 목록을 다시 불러온다 — 되돌린 판본이 대기함으로 돌아와야 한다", async () => {
    const user = userEvent.setup();
    renderPage();
    await run(user);
    const before = findCalls(PENDING).length;
    await user.click(screen.getByRole("button", { name: /자동 판정만 되돌리기/ }));
    await user.click(await screen.findByRole("button", { name: "되돌리기" }));

    await waitFor(() => expect(findCalls(PENDING).length).toBeGreaterThan(before));
  });

  it("되돌리기가 실패하면 안내한다", async () => {
    const user = userEvent.setup();
    renderPage([{ url: UNDO, method: "POST", status: 500, error: "INTERNAL_ERROR" }]);
    await run(user);
    await user.click(screen.getByRole("button", { name: /자동 판정만 되돌리기/ }));
    await user.click(await screen.findByRole("button", { name: "되돌리기" }));

    await findText("되돌리지 못했어요");
  });
});
