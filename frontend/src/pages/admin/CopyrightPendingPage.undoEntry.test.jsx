import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CopyrightPendingPage } from "./CopyrightPendingPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, fakeResponse, findCalls } from "../../test/apiMock.js";
import { adminComposers, pageResponse, pendingCopyrightResponse } from "../../test/fixtures.js";
import { expectNoText, expectText, findText, waitForNoText } from "../../test/text.js";

/**
 * "자동 판정만 되돌리기" 의 **진입점** — 02_API_명세서 §5-8-1 (2026-09-09 신설, senior-dev. qa 5차 결함 1. **Red**).
 *
 * 지금까지 이 버튼은 `AutoJudgePanel` 의 **로컬 state(`result`)** 로만 그려졌다. 그래서
 * 관리 홈에 갔다 오거나 새로고침하면 사라지고, 재실행해도 멱등이라 `judgedFree === 0` 이면 다시 나타나지 않는다.
 * API 는 살아 있는데 **화면에 들어가는 문이 없다** — 8084 실데이터가 이미 그 상태다.
 *
 * 이 되돌리기는 기획 `02_저작권_판정_지침.md` 부록 A §A-2 ④ 가 "출판 후 120년" 이라는 통계적 안전선을
 * 정당화하는 근거("한 번의 요청으로 전부 되돌릴 수 있게 한다")라, 도달 불가면 규칙의 근거가 함께 약해진다.
 *
 * 그래서 버튼의 노출 조건을 **서버가 주는 잔량**(`autoJudged.revertibleEditions`)으로 옮긴다.
 * 화면은 "내가 방금 실행했나" 를 기억하는 대신 "되돌릴 것이 남아 있나" 를 매 조회마다 다시 안다.
 */
const PENDING = /\/api\/admin\/copyright\/pending/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const AUTO_JUDGE = /\/api\/admin\/copyright\/auto-judge$/;
const UNDO = /\/api\/admin\/copyright\/auto-judge\/undo$/;

const UNDO_BUTTON = { name: /자동 판정만 되돌리기/ };

/** 자동 판정을 이미 돌려 둔 상태(다른 날 · 다른 세션에서 실행했고, 지금은 새로고침으로 들어왔다). */
function alreadyJudged({ revertibleEditions = 812, revertibleRecommendedWorks = 17 } = {}) {
  return pendingCopyrightResponse({ autoJudged: { revertibleEditions, revertibleRecommendedWorks } });
}

function renderPage(routes = []) {
  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: PENDING, method: "GET", data: pendingCopyrightResponse() },
    { url: UNDO, method: "POST", data: { reverted: 812, recommendationKept: 17 } },
    ...routes,
  ]);
  return renderWithProviders(<CopyrightPendingPage />, {
    route: "/admin/copyright",
    path: "/admin/copyright",
    auth: "admin",
  });
}

describe("대기함 — 되돌리기 진입점 (02 §5-8-1)", () => {
  it("실행하지 않고 들어와도 되돌릴 것이 남아 있으면 버튼이 있다 (새로고침·재방문에서 사라지지 않는다)", async () => {
    renderPage([{ url: PENDING, method: "GET", data: alreadyJudged() }]);
    await findText("저작권 판정 대기함");

    expect(screen.getByRole("button", UNDO_BUTTON)).toBeInTheDocument();
  });

  it("되돌릴 것이 없으면 버튼도 없다 — 누를 수 없는 버튼을 두지 않는다", async () => {
    renderPage([{ url: PENDING, method: "GET", data: alreadyJudged({ revertibleEditions: 0, revertibleRecommendedWorks: 0 }) }]);
    await findText("저작권 판정 대기함");

    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();
  });

  it("사람이 대부분 다시 판정해 1개만 남아도 버튼이 있다 — 부분적으로도 되돌릴 수 있다", async () => {
    renderPage([{ url: PENDING, method: "GET", data: alreadyJudged({ revertibleEditions: 1, revertibleRecommendedWorks: 0 }) }]);
    await findText("저작권 판정 대기함");

    expect(screen.getByRole("button", UNDO_BUTTON)).toBeInTheDocument();
    expectText("되돌릴 수 있는 자동 판정 1개");
  });

  it("대기 목록이 비어 있어도 버튼이 있다 — 전부 자동으로 연 직후가 되돌리기가 가장 필요한 때다", async () => {
    renderPage([
      {
        url: PENDING,
        method: "GET",
        data: pendingCopyrightResponse({
          editions: [],
          unfilteredTotal: 0,
          autoJudged: { revertibleEditions: 812, revertibleRecommendedWorks: 17 },
        }),
      },
    ]);
    await findText("확인 중인 판본이 없어요");

    expect(screen.getByRole("button", UNDO_BUTTON)).toBeInTheDocument();
  });

  it("남은 양을 말한다 — 몇 개를 되돌릴 수 있고 되돌리면 몇 곡이 닫히는지", async () => {
    renderPage([{ url: PENDING, method: "GET", data: alreadyJudged() }]);
    await findText("저작권 판정 대기함");

    expectText("되돌릴 수 있는 자동 판정 812개");
    expectText("되돌리면 17곡의 다운로드가 닫혀요");
  });

  it("닫히는 곡이 없으면 곡 수를 말하지 않는다 — '0곡' 을 쓰지 않는다", async () => {
    renderPage([{ url: PENDING, method: "GET", data: alreadyJudged({ revertibleRecommendedWorks: 0 }) }]);
    await findText("저작권 판정 대기함");

    expectText("되돌릴 수 있는 자동 판정 812개");
    expectNoText("0곡");
    expectNoText("다운로드가 닫혀요");
  });

  it("그 버튼으로 실제 되돌릴 수 있다 — 확인 뒤 §5-12 를 부르고 목록을 다시 읽는다", async () => {
    const user = userEvent.setup();
    renderPage([{ url: PENDING, method: "GET", data: alreadyJudged() }]);
    await findText("저작권 판정 대기함");
    const before = findCalls(PENDING).length;

    await user.click(screen.getByRole("button", UNDO_BUTTON));
    await user.click(await screen.findByRole("button", { name: "되돌리기" }));

    await waitFor(() => expect(findCalls(UNDO)).toHaveLength(1));
    expect(findCalls(UNDO)[0].body).toBeUndefined();
    await waitFor(() => expect(findCalls(PENDING).length).toBeGreaterThan(before));
    await findText("자동으로 지정된 추천 판본은 그대로 있어요");
  });

  it("되돌린 뒤 다시 읽은 값이 0 이면 버튼과 안내가 함께 사라진다", async () => {
    const user = userEvent.setup();
    let pendingCalls = 0;
    renderPage([
      {
        url: PENDING,
        method: "GET",
        // 첫 조회는 "되돌릴 것 있음", 되돌린 뒤의 재조회는 "없음" — 서버 상태를 그대로 따라간다
        handler: () => {
          pendingCalls += 1;
          const data = pendingCalls === 1 ? alreadyJudged() : alreadyJudged({ revertibleEditions: 0, revertibleRecommendedWorks: 0 });
          return fakeResponse({ success: true, message: "성공", data });
        },
      },
    ]);
    await findText("되돌릴 수 있는 자동 판정 812개");

    await user.click(screen.getByRole("button", UNDO_BUTTON));
    await user.click(await screen.findByRole("button", { name: "되돌리기" }));

    await waitForNoText("되돌릴 수 있는 자동 판정 812개");
    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();
  });

  it("실행 직후에는 재조회가 끝나기 전에도 버튼이 남는다 — 방금 연 것을 바로 닫을 수 있어야 한다", async () => {
    const user = userEvent.setup();
    renderPage([
      // 서버는 아직 "되돌릴 것 없음" 을 주고 있다(재조회 전) — 그래도 이번 세션의 실행 결과로 버튼이 보인다
      { url: PENDING, method: "GET", data: pendingCopyrightResponse() },
      {
        url: AUTO_JUDGE,
        method: "POST",
        data: {
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
        },
      },
    ]);
    await findText("저작권 판정 대기함");
    await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));
    await screen.findByRole("dialog");
    await user.click(screen.getByRole("button", { name: "실행" }));
    await findText("판정했어요");

    expect(screen.getByRole("button", UNDO_BUTTON)).toBeInTheDocument();
  });
});
