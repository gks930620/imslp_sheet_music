import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CopyrightPendingPage } from "./CopyrightPendingPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, fakeResponse, findCalls } from "../../test/apiMock.js";
import { adminComposers, pageResponse, pendingCopyrightResponse } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

/**
 * 되돌리기 **직후의 창** — 02_API_명세서 §5-8-1 화면 계약 2·2-1·2-2 (2026-09-09 개정, senior-dev. **Red**).
 *
 * 되돌리기 응답이 온 순간, 화면이 들고 있는 `autoJudged` 는 **내가 방금 되돌린 것을 세고 있는 철 지난 값**이다.
 * 재조회가 도착하기 전까지 화면은 "812개를 되돌렸어요" 와 "되돌릴 수 있는 자동 판정 812개" 를 **동시에** 말한다.
 * 데이터 피해는 없지만 화면이 사실과 다른 말을 하고, 그 상태에서 한 번 더 누르면 `reverted: 0` 응답이
 * 방금 뜬 결과 안내를 "되돌릴 자동 판정이 없었어요" 로 덮는다.
 *
 * <h2>잠그는 성질</h2>
 * 로컬 기억(내가 방금 무엇을 했나)은 **내 동작 직전의 서버 값에만** 효력이 있고, **새 §5-8 응답이 도착하는 순간 무효**다.
 * 그래서 되돌린 직후에는 버튼·잔량 줄이 사라지고(연타 자체가 불가능해진다), 새 응답이 `revertibleEditions > 0`
 * 을 주면 **다시 보인다** — 그 사이 다른 관리자가 자동 판정을 돌렸을 수 있고, 이 진입점은 **감추는 방향으로
 * 틀리면 안 된다**(qa 5차 결함 1: 있는데 감춰 안전장치가 도달 불가였다).
 *
 * 반대로 §5-11 실행 뒤의 기억은 **더 보이게만** 하므로 그대로 둔다 — `undoEntry.test.jsx` 의
 * "실행 직후에는 재조회가 끝나기 전에도 버튼이 남는다" 가 그 계약이고, 이 파일은 그것을 깨지 않는다.
 *
 * <h2>재조회를 손으로 붙잡는 이유</h2>
 * 이 파일이 보는 구간은 "되돌리기 응답은 왔는데 재조회는 아직" 이다. 목이 즉시 응답하면 그 창이 없어
 * 무엇도 관찰할 수 없으므로, 두 번째 조회부터는 테스트가 `release()` 로 풀어 줄 때까지 도착하지 않게 한다.
 */
const PENDING = /\/api\/admin\/copyright\/pending/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const AUTO_JUDGE = /\/api\/admin\/copyright\/auto-judge$/;
const UNDO = /\/api\/admin\/copyright\/auto-judge\/undo$/;

const UNDO_BUTTON = { name: /자동 판정만 되돌리기/ };

/** 자동 판정이 이미 돌아간 상태(§5-8-1). `unfilteredTotal` 은 재조회 도착을 눈으로 확인하는 표식으로 쓴다. */
function judged({ revertibleEditions = 812, revertibleRecommendedWorks = 17, unfilteredTotal = 19 } = {}) {
  return pendingCopyrightResponse({ unfilteredTotal, autoJudged: { revertibleEditions, revertibleRecommendedWorks } });
}

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

/** 첫 조회는 바로 온다. **재조회는 `release()` 를 부를 때까지 도착하지 않는다.** */
function renderPage({ first = judged(), next = judged({ revertibleEditions: 0, revertibleRecommendedWorks: 0, unfilteredTotal: 7 }) } = {}) {
  let release;
  const held = new Promise((resolve) => {
    release = resolve;
  });
  let calls = 0;

  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: AUTO_JUDGE, method: "POST", data: RUN_RESULT },
    { url: UNDO, method: "POST", data: { reverted: 812, recommendationKept: 17 } },
    {
      url: PENDING,
      method: "GET",
      handler: async () => {
        calls += 1;
        if (calls > 1) await held;
        return fakeResponse({ success: true, message: "성공", data: calls === 1 ? first : next });
      },
    },
  ]);

  renderWithProviders(<CopyrightPendingPage />, {
    route: "/admin/copyright",
    path: "/admin/copyright",
    auth: "admin",
  });
  return { release };
}

/** 확인 모달까지 지나 실제로 되돌린다. 재조회는 아직 도착하지 않은 상태로 남는다. */
async function undo(user) {
  await user.click(screen.getByRole("button", UNDO_BUTTON));
  await user.click(await screen.findByRole("button", { name: "되돌리기" }));
  await findText("저작권 판정 812개를 되돌렸어요");
}

describe("대기함 — 되돌린 직후의 창 (02 §5-8-1 화면 계약 2-1·2-2)", () => {
  it("되돌린 직후에는 옛 잔량을 말하지 않는다 — 방금 되돌린 것을 '되돌릴 수 있다' 고 하지 않는다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("되돌릴 수 있는 자동 판정 812개");

    await undo(user);

    expectNoText("되돌릴 수 있는 자동 판정 812개");
    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();
  });

  it("그래서 연타가 불가능하다 — 두 번째 요청이 나가지 않고 결과 안내가 덮이지 않는다", async () => {
    const user = userEvent.setup();
    const { release } = renderPage();
    await findText("되돌릴 수 있는 자동 판정 812개");

    await undo(user);
    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();

    release();
    await findText("확인 중인 판본 7개");

    expect(findCalls(UNDO)).toHaveLength(1);
    expectText("저작권 판정 812개를 되돌렸어요");
    expectNoText("되돌릴 자동 판정이 없었어요");
  });

  it("새 응답이 되돌릴 것이 있다고 하면 버튼이 돌아온다 — 그 사이 다른 관리자가 실행했을 수 있다", async () => {
    const user = userEvent.setup();
    const { release } = renderPage({
      next: judged({ revertibleEditions: 500, revertibleRecommendedWorks: 9, unfilteredTotal: 7 }),
    });
    await findText("되돌릴 수 있는 자동 판정 812개");

    await undo(user);
    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();

    release();
    await findText("되돌릴 수 있는 자동 판정 500개");

    expect(screen.getByRole("button", UNDO_BUTTON)).toBeInTheDocument();
    expectText("되돌리면 9곡의 다운로드가 닫혀요");
  });

  it("실행 직후 창에서 되돌리면 실행 기억도 함께 끝난다 — 되돌린 것을 다시 되돌릴 수는 없다", async () => {
    const user = userEvent.setup();
    renderPage({ first: judged({ revertibleEditions: 0, revertibleRecommendedWorks: 0 }) });
    await findText("저작권 판정 대기함");
    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();

    // 서버 잔량은 아직 0 이고 버튼은 이번 세션의 실행 기억으로만 서 있다(화면 계약 2)
    await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));
    await screen.findByRole("dialog");
    await user.click(screen.getByRole("button", { name: "실행" }));
    await findText("판정했어요");
    expect(screen.getByRole("button", UNDO_BUTTON)).toBeInTheDocument();

    await undo(user);

    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();
  });

  it("되돌린 뒤 다시 실행하면 버튼이 돌아온다 — 화면은 마지막 동작 하나만 기억한다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("되돌릴 수 있는 자동 판정 812개");

    await undo(user);
    expect(screen.queryByRole("button", UNDO_BUTTON)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));
    await screen.findByRole("dialog");
    await user.click(screen.getByRole("button", { name: "실행" }));
    await findText("판정했어요");

    expect(screen.getByRole("button", UNDO_BUTTON)).toBeInTheDocument();
  });
});
