import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { workDetail, edition, editionOtherFree } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

/**
 * "다른 판본 보기" 목록과 IMSLP 안내 줄 — 기획 §F3-4 · §10-1, 02_API_명세서 §3-3
 * (2026-09-08 계약 통일, senior-dev. qa 4차 결함 4. Red).
 *
 * <p>줄로 펼치는 것은 <b>우리가 파일을 가진 판본</b>뿐이다. 파일 없는 판본은 줄을 만들지 않고
 * 접힌 영역 맨 아래 회색 한 줄 "IMSLP 에는 이 곡의 다른 악보가 N개 더 있어요" 로 대신한다.
 * N 은 서버가 주는 {@code imslpOnlyCount}(추천 제외 · 파일 없는 판본 수)이고, 링크는 곡의 IMSLP 작품 페이지다.
 *
 * <p>지금 화면은 서버가 주던 "파일 없는 5줄" 을 그대로 그리고 안내 줄은 만들지 않았다 —
 * 두 설계의 나쁜 점만 남아 있었다(qa 4차 실측: 파일 없는 줄 5개 · 안내 줄 0개).
 */
function renderDetail(data) {
  mockFetch([
    { url: "/api/editions/301/download", method: "HEAD", raw: "" },
    { url: /\/api\/works\/21$/, data },
  ]);
  return renderWithProviders(<WorkDetailPage />, { route: "/works/21", path: "/works/:id" });
}

const IMSLP_WORK_URL = "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)";

describe("WorkDetailPage — 다른 판본 목록과 IMSLP 안내 줄 (기획 §F3-4)", () => {
  it("헤더 개수는 '파일 있는 판본' 수이고, 펼치면 그 줄들 + 맨 아래 IMSLP 안내 한 줄", async () => {
    renderDetail(
      workDetail({
        otherEditions: [editionOtherFree, edition({ id: 303, koreaCopyright: "UNKNOWN", previewUrl: null, downloadable: false, downloadUrl: null })],
        imslpOnlyCount: 88,
        downloadableOtherCount: 1,
      }),
    );
    await findText("월광 소나타");

    expect(await screen.findByRole("button", { name: /다른 판본 보기 \(2개\)/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /다른 판본 보기/ }));

    expectText("IMSLP 에는 이 곡의 다른 악보가 88개 더 있어요");
    const link = screen.getByRole("link", { name: /IMSLP 에서 보기/ });
    expect(link).toHaveAttribute("href", IMSLP_WORK_URL);
    expect(link).toHaveAttribute("target", "_blank");
  });

  it("파일 있는 다른 판본이 0개면 안내 한 줄만 남는다 — 접이식 목록은 만들지 않는다", async () => {
    renderDetail(workDetail({ otherEditions: [], imslpOnlyCount: 88 }));
    await findText("월광 소나타");

    expectText("IMSLP 에는 이 곡의 다른 악보가 88개 더 있어요");
    expectNoText("다른 판본 보기");
  });

  it("IMSLP 판본도 0개면 영역 자체가 없다", async () => {
    renderDetail(workDetail({ otherEditions: [], imslpOnlyCount: 0 }));
    await findText("월광 소나타");

    expectNoText("IMSLP 에는 이 곡의 다른 악보가");
    expectNoText("다른 판본 보기");
  });

  it("곡의 IMSLP 주소가 없으면 안내 줄도 만들지 않는다 — 보낼 곳이 없는 안내는 안내가 아니다", async () => {
    renderDetail(workDetail({ otherEditions: [], imslpOnlyCount: 88, imslpUrl: null }));
    await findText("월광 소나타");

    expectNoText("IMSLP 에는 이 곡의 다른 악보가");
  });
});
