import { screen } from "@testing-library/react";
import { SearchResultPage } from "./SearchResultPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { searchResponse, popularWorks, workNocturne2 } from "../../test/fixtures.js";
import { findText } from "../../test/text.js";

/**
 * 검색 0건의 IMSLP 출구 — 기획 §5 예외표(검색 결과 0건) · §10-7 · §6 인수조건
 * "검색 0건 화면에 'IMSLP 에서 직접 찾아보기' 링크가 있고, 누르면 그 검색어로 IMSLP 검색 결과가 새 탭에 열린다."
 * (frontend-dev 작성. 화면 정의서 02 §상태별 UI 에는 아직 이 줄이 없어 designer 에게 넘긴다.)
 *
 * <p>0건이 "세상에 없어요" 가 아니라 "우리가 1차 범위에서 일부러 뺐어요" 인 경우가 많다
 * (캐논·사랑의 인사 등 `03_1차_큐레이션_곡목록.md` §3). 그때 "없어요" 로 끝내면 사용자는 목적을 못 이루고 나간다.
 */
const SEARCH = /\/api\/works\/search/;

function renderSearch(route, routes) {
  mockFetch(routes);
  return renderWithProviders(<SearchResultPage />, { route, path: "/search" });
}

function imslpExit() {
  return screen.queryByRole("link", { name: /IMSLP 에서 직접 찾아보기/ });
}

describe("SearchResultPage — 검색 0건의 IMSLP 출구 (기획 §10-7)", () => {
  it("검색어를 실은 IMSLP 검색 주소를 새 탭으로 연다", async () => {
    renderSearch("/search?q=캐논", [
      { url: SEARCH, data: searchResponse({ q: "캐논", works: [], totalElements: 0, unfilteredTotal: 0 }) },
      { url: "/api/works/popular", data: popularWorks(5) },
    ]);
    await findText("'캐논'에 맞는 곡을 찾지 못했어요");

    expect(imslpExit()).toHaveAttribute(
      "href",
      "https://imslp.org/index.php?title=Special:Search&search=%EC%BA%90%EB%85%BC",
    );
    expect(imslpExit()).toHaveAttribute("target", "_blank");
    expect(imslpExit()).toHaveAttribute("rel", expect.stringContaining("noreferrer"));
  });

  it("결과가 있으면 이 링크는 없다 — 출구는 막힌 사람에게만 필요하다", async () => {
    renderSearch("/search?q=녹턴", [{ url: SEARCH, data: searchResponse({ works: [workNocturne2] }) }]);
    await findText("녹턴 2번");

    expect(imslpExit()).not.toBeInTheDocument();
  });

  it("필터 때문에 0건이면 이 링크는 없다 — 그 사람의 출구는 '필터 해제'다", async () => {
    renderSearch("/search?q=녹턴&level=BEGINNER", [
      { url: SEARCH, data: searchResponse({ works: [], totalElements: 0, unfilteredTotal: 21 }) },
    ]);
    await findText("이 조건에 맞는 곡이 없어요");

    expect(imslpExit()).not.toBeInTheDocument();
  });
});
