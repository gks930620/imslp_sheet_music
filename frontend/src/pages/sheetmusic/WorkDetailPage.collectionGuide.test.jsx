import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { workDetail } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

/**
 * 수록곡 안내 `collectionGuide` — 기획 §2 F3-2 · §10-3, 02_API_명세서 §3-3
 * (frontend-dev 작성. 서버는 38곡에 값을 주는데 화면이 그리지 않아 qa 4차에서 결함이 됐다).
 *
 * <p>검색 결과에서 "'강아지 왈츠'가 들어 있는 악보" · "(전곡 기준)" 을 보고 들어온 사용자에게
 * "그래서 내 곡이 몇 번인지" 를 말해 주는 유일한 줄이다. 이 줄이 없으면 "N곡 묶음" 을 2차로 미룬 근거가 무너진다.
 *
 * <p>문구는 서버가 준 <b>완성 문장 그대로</b> 한 줄이다 — 화면이 조립하지 않는다(§3-3: "서버가 조립하지 않는다").
 * null/공백이면 줄 자체를 만들지 않는다.
 */
const GUIDE = "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요";

function renderDetail(data) {
  mockFetch([
    { url: "/api/editions/301/download", method: "HEAD", raw: "" },
    { url: /\/api\/works\/21$/, data },
  ]);
  return renderWithProviders(<WorkDetailPage />, { route: "/works/21", path: "/works/:id" });
}

describe("WorkDetailPage — 수록곡 안내 (기획 §F3-2)", () => {
  it("값이 있으면 곡 정보 영역에 그 문장 그대로 한 줄", async () => {
    renderDetail(workDetail({ collectionGuide: GUIDE }));
    await findText("월광 소나타");

    expectText(GUIDE);
  });

  it("악장 안내와는 다른 줄이다 — 둘 다 있으면 둘 다 보인다", async () => {
    renderDetail(workDetail({ collectionGuide: GUIDE }));
    await findText("월광 소나타");

    expectText(GUIDE);
    expectText("악장 안내: 1악장 1쪽 · 2악장 6쪽 · 3악장 9쪽 (추천 판본 기준)");
  });

  it("준비 중(추천 판본 없음) 곡에서도 보인다 — 묶음이라는 사실은 판본과 무관하다", async () => {
    renderDetail(
      workDetail({ status: "PREPARING", recommendedEdition: null, collectionGuide: GUIDE }),
    );
    await findText("악보를 준비하고 있어요");

    expectText(GUIDE);
  });

  it("null 이면 줄을 만들지 않는다", async () => {
    renderDetail(workDetail({ collectionGuide: null }));
    await findText("월광 소나타");

    expectNoText("들어 있어요");
  });

  it("공백만 있어도 줄을 만들지 않는다 (§3-3 'null/공백이면 화면에서 줄 생략')", async () => {
    renderDetail(workDetail({ collectionGuide: "   " }));
    await findText("월광 소나타");

    expect(document.querySelector(".work-detail-collection")).toBeNull();
  });
});
