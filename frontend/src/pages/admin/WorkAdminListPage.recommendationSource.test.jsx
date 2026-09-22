import { screen, within } from "@testing-library/react";
import { WorkAdminListPage } from "./WorkAdminListPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { adminComposers, adminWorkSummary, adminWorksResponse, pageResponse } from "../../test/fixtures.js";
import { expectNoText, findText } from "../../test/text.js";

/**
 * 곡 목록(관리)의 추천 열 — 02 §4-6 `recommendationSource` (기획 06 §2-2, 화면정의 05 화면 D).
 *
 * <p>지금까지 이 열은 <b>있다(★)/없다(–)</b> 만 말했다. 관리자가 42곡을 훑을 때 던지는 질문은
 * <b>"어느 곡이 아직 사람 손을 안 거쳤나"</b> 인데, 그걸 알려면 곡을 하나씩 열어 봐야 했다 — 그러면 훑는 게 성립하지 않는다.
 *
 * <p>값은 넷이고 <b>"기록 없음" 은 enum 상수가 아니라 null</b> 이다(§4-6):
 * 추천이 있는데 {@code recommendationSource} 가 없으면 그것이 곧 "기록 없음"(실데이터 42곡)이다.
 */
const WORKS = /\/api\/admin\/works(\?|$)/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;

function renderPage(works) {
  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: WORKS, method: "GET", data: adminWorksResponse({ works }) },
  ]);
  return renderWithProviders(<WorkAdminListPage />, { route: "/admin/works", path: "/admin/works", auth: "admin" });
}

function workRow(titleText) {
  return screen.getByText(titleText).closest(".data-table-row, tr, .admin-work-card") ?? document.body;
}

describe("WorkAdminListPage — 추천을 누가 골랐나 (02 §4-6)", () => {
  it("자동으로 지정된 곡은 '자동' 으로 보인다 (8-A 7)", async () => {
    renderPage([adminWorkSummary({ hasRecommended: true, recommendationSource: "AUTO" })]);
    await findText("월광 소나타");

    expect(within(workRow("월광 소나타")).getByText("자동")).toBeInTheDocument();
  });

  it("사람이 고른 곡은 '사람' 으로 보인다", async () => {
    renderPage([adminWorkSummary({ hasRecommended: true, recommendationSource: "ADMIN" })]);
    await findText("월광 소나타");

    expect(within(workRow("월광 소나타")).getByText("사람")).toBeInTheDocument();
  });

  it("추천은 있는데 기록이 없으면 '기록 없음' 이다 — 실데이터 42곡의 정상 상태 (8-E 1)", async () => {
    renderPage([adminWorkSummary({ hasRecommended: true, recommendationSource: null })]);
    await findText("월광 소나타");

    expect(within(workRow("월광 소나타")).getByText("기록 없음")).toBeInTheDocument();
  });

  it("추천이 없는 곡은 지금처럼 비어 있다 — 자동/사람/기록 없음 중 어느 것도 아니다", async () => {
    renderPage([adminWorkSummary({ hasRecommended: false, recommendationSource: null })]);
    await findText("월광 소나타");

    expectNoText("기록 없음");
    expect(within(workRow("월광 소나타")).queryByText("자동")).not.toBeInTheDocument();
    expect(within(workRow("월광 소나타")).queryByText("사람")).not.toBeInTheDocument();
  });

  it("미검수 표시와 함께 나올 수 있다 — 둘은 다른 질문의 답이다 (기획 §2-2)", async () => {
    renderPage([adminWorkSummary({
      hasRecommended: true, recommendationSource: "AUTO", recommendationReviewed: false,
    })]);
    await findText("월광 소나타");

    const row = within(workRow("월광 소나타"));
    expect(row.getByText("자동")).toBeInTheDocument();
    expect(row.getByText("미검수")).toBeInTheDocument();
  });

  it("자동으로 지정됐지만 사람이 미리보기를 확인한 곡은 '자동 + 검수 완료' 로 남는다", async () => {
    renderPage([adminWorkSummary({
      hasRecommended: true, recommendationSource: "AUTO", recommendationReviewed: true,
    })]);
    await findText("월광 소나타");

    expect(within(workRow("월광 소나타")).getByText("자동")).toBeInTheDocument();
    expect(screen.queryByText("미검수")).not.toBeInTheDocument();
  });

  it("이번 기능 때문에 새 상태 필터가 생기지 않았다 (8-E 4) — 필터는 7개 그대로다", async () => {
    renderPage([adminWorkSummary()]);
    await findText("월광 소나타");

    expectNoText("근거 없음만");
    expectNoText("기록 없는 곡");
  });
});
