import { screen } from "@testing-library/react";
import { AdminDashboardPage } from "./AdminDashboardPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { dashboard } from "../../test/fixtures.js";
import { findText } from "../../test/text.js";

/**
 * 관리 홈 "추천 판본 확인 필요 N곡" 카드 — 02_API_명세서 §4-1 · §5-6-1, 기획 §F6-4 · §8-17 · §10-8
 * (frontend-dev 작성. 계약·백엔드에는 `needsRecommendationReviewWorks` 가 있는데 화면에 없었다).
 *
 * <p>자동 추천 지정은 "전체 악보 · 전곡" 만 보므로 <b>관현악 총보가 추천이 될 수 있다.</b>
 * 그래서 공개(출시) 기준이 "추천 판본 미검수 0곡" 인데, 화면에 숫자가 없으면 <b>출시를 막는 조건을 셀 수 없다.</b>
 *
 * <p>카드는 §4-6 `status=NEEDS_RECOMMENDATION_REVIEW` 목록과 <b>같은 모집단</b>이라 그 목록으로 이어진다.
 */
function renderDashboard(data) {
  mockFetch([
    { url: "/api/admin/dashboard", data },
    { url: "/api/admin/crawl/jobs/active", data: null },
  ]);
  return renderWithProviders(<AdminDashboardPage />, { route: "/admin", path: "/admin", auth: "admin" });
}

function statCard(label) {
  return screen.getByText(label).closest("a, div");
}

describe("AdminDashboardPage — 추천 판본 확인 필요 카드 (02 §4-1)", () => {
  it("카드 라벨과 값을 보여준다", async () => {
    renderDashboard(dashboard({ needsRecommendationReviewWorks: 12 }));
    await findText("전체 곡");

    expect(statCard("추천 판본 확인 필요")).toHaveTextContent("12");
  });

  it("같은 모집단인 곡 목록 필터로 이어진다", async () => {
    renderDashboard(dashboard({ needsRecommendationReviewWorks: 12 }));
    await findText("전체 곡");

    expect(screen.getByRole("link", { name: /^추천 판본 확인 필요/ })).toHaveAttribute(
      "href",
      "/admin/works?status=NEEDS_RECOMMENDATION_REVIEW",
    );
  });

  it("0곡이어도 카드는 남는다 — 출시 기준을 재는 숫자라 '0' 이 결과다", async () => {
    renderDashboard(dashboard({ needsRecommendationReviewWorks: 0 }));
    await findText("전체 곡");

    expect(statCard("추천 판본 확인 필요")).toHaveTextContent("0");
  });
});
