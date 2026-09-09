import { screen, within } from "@testing-library/react";
import { WorkAdminListPage } from "./WorkAdminListPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { adminComposers, adminWorkSummary, adminWorksResponse, pageResponse } from "../../test/fixtures.js";
import { findText } from "../../test/text.js";

/**
 * 곡 목록(관리)의 "미검수" 표시 — 02_API_명세서 §4-6 `recommendationReviewed` · §5-6-1
 * (frontend-dev 작성).
 *
 * <p>추천이 <b>있는데</b> 사람 눈을 통과하지 않은 곡을 목록에서 바로 알아보기 위한 표시다.
 * 추천이 없는 곡은 검수할 대상이 없으므로 표시하지 않는다(§4-6: "hasRecommended == false 인 곡은 항상 false").
 *
 * <p>서버가 아직 그 필드를 안 내리면(구버전) 표시하지 않는다 — 없는 것을 "미검수"라고 단정하지 않는다.
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

describe("WorkAdminListPage — 추천 판본 미검수 표시 (02 §4-6)", () => {
  it("추천이 있고 미검수면 '미검수' 를 단다", async () => {
    renderPage([adminWorkSummary({ hasRecommended: true, recommendationReviewed: false })]);
    await findText("월광 소나타");

    expect(within(workRow("월광 소나타")).getByText("미검수")).toBeInTheDocument();
  });

  it("확인이 끝난 곡에는 달지 않는다", async () => {
    renderPage([adminWorkSummary({ hasRecommended: true, recommendationReviewed: true })]);
    await findText("월광 소나타");

    expect(screen.queryByText("미검수")).not.toBeInTheDocument();
  });

  it("추천이 없는 곡에는 달지 않는다 — 검수할 대상이 없다", async () => {
    renderPage([adminWorkSummary({ hasRecommended: false, recommendationReviewed: false })]);
    await findText("월광 소나타");

    expect(screen.queryByText("미검수")).not.toBeInTheDocument();
  });

  it("서버가 그 필드를 안 내리면 달지 않는다", async () => {
    // 픽스처는 계약대로 recommendationReviewed 를 갖는다(2026-09-08 senior-dev). "구버전 서버"는
    // 값이 false 인 것이 아니라 **키가 없는 것**이므로, 그 상태를 여기서 명시적으로 만든다.
    const legacy = adminWorkSummary({ hasRecommended: true });
    delete legacy.recommendationReviewed;
    renderPage([legacy]);
    await findText("월광 소나타");

    expect(screen.queryByText("미검수")).not.toBeInTheDocument();
  });
});
