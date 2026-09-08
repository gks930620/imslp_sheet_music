import { screen } from "@testing-library/react";
import { SearchResultPage } from "./SearchResultPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { searchResponse } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// frontend-dev 가 직접 쓴 보완 테스트 — 기획 §11-4(2026-09-08). API 변경 없음(workCount 그대로).
// 작곡가 카드의 곡 수는 필터·페이지와 무관한 "작곡가 페이지에 가면 있는 곡 수"라,
// 결과 0곡과 나란히 놓여도 모순이 아니라는 것을 문구로 잇는다. 배치 확정은 designer.
const SEARCH = /\/api\/works\/search/;
const chopin = { id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", workCount: 10 };

function renderSearch(route, routes) {
  mockFetch(routes);
  return renderWithProviders(<SearchResultPage />, { route, path: "/search" });
}

describe("SearchResultPage — 작곡가 카드 문구·0건일 때의 출구 (기획 §11-4)", () => {
  it("작곡가 카드 문구는 '곡 10개' 가 아니라 '등록된 곡 10개 모두 보기'", async () => {
    renderSearch("/search?q=쇼팽", [
      { url: SEARCH, data: searchResponse({ q: "쇼팽", composers: [chopin], composerMatchCount: 1 }) },
    ]);
    await findText("등록된 곡 10개 모두 보기");
  });

  it("필터로 0건이 되어도 카드는 남고, 빈 결과 안내에 '쇼팽의 곡은 10개 등록돼 있어요 — 조건 없이 모두 보기'", async () => {
    renderSearch("/search?q=쇼팽&level=BEGINNER&downloadable=true", [
      {
        url: SEARCH,
        data: searchResponse({ q: "쇼팽", works: [], totalElements: 0, unfilteredTotal: 21, composers: [chopin], composerMatchCount: 1 }),
      },
    ]);
    await findText("이 조건에 맞는 곡이 없어요");
    expectText("쇼팽의 곡은 10개 등록돼 있어요 — 조건 없이 모두 보기");
    expect(screen.getByRole("link", { name: /조건 없이 모두 보기/ })).toHaveAttribute("href", "/composers/9");
  });

  it("작곡가 카드가 없으면 그 한 줄도 없다", async () => {
    renderSearch("/search?q=녹턴&level=BEGINNER", [
      { url: SEARCH, data: searchResponse({ works: [], totalElements: 0, unfilteredTotal: 21 }) },
    ]);
    await findText("이 조건에 맞는 곡이 없어요");
    expectNoText("조건 없이 모두 보기");
  });
});
