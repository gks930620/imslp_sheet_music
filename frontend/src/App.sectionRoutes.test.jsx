import { waitFor } from "@testing-library/react";
import App from "./App.jsx";
import { renderWithProviders, readLocation } from "./test/renderWithProviders.jsx";
import { mockFetch } from "./test/apiMock.js";
import {
  composerDetail,
  composerListResponse,
  composerWorksResponse,
  dashboard,
  popularWorks,
  featuredComposers,
  searchResponse,
  workDetail,
} from "./test/fixtures.js";
import { findText } from "./test/text.js";

// 03_기술결정 §21 주소 구조 · 02 §0-5 라우트 표 · 기획 04 §3-5(옛 주소) · 08 §5(탭 제목)
// 옛 주소는 죽지 않는다 — 같은 화면의 /piano/… 로 갈아탄다. 쿼리는 한 글자도 잃지 않는다.

function renderApp(route, auth = "guest") {
  mockFetch([
    { url: /\/api\/works\/search/, data: { ...searchResponse(), in: "ALL", totalInAll: null } },
    { url: /\/api\/works\/popular/, data: popularWorks(3) },
    { url: /\/api\/composers\/featured/, data: featuredComposers(3) },
    { url: /\/api\/composers\/\d+\/works/, data: composerWorksResponse() },
    { url: /\/api\/composers\/\d+(\?|$)/, data: composerDetail() },
    { url: /\/api\/composers(\?|$)/, data: composerListResponse },
    { url: /\/api\/works\/\d+(\?|$)/, data: { ...workDetail(), section: "PIANO" } },
    { url: /\/api\/admin\/dashboard/, data: dashboard() },
    { url: "/api/admin/crawl/jobs/active", data: null },
  ]);
  return renderWithProviders(<App />, { route, auth });
}

describe("옛 주소는 피아노 구분으로 이어진다 (기획 04 §3-5, 인수 조건 8-B 5)", () => {
  it.each([
    ["/", "/piano", ""],
    ["/search?q=월광", "/piano/search", "?q=월광"],
    ["/search?q=월광&level=INTERMEDIATE&pages=LE10&downloadable=true&page=2", "/piano/search",
      "?q=월광&level=INTERMEDIATE&pages=LE10&downloadable=true&page=2"],
    ["/works/21", "/piano/works/21", ""],
    ["/composers", "/piano/composers", ""],
    ["/composers/9", "/piano/composers/9", ""],
    ["/composers/9?sort=opus&page=1", "/piano/composers/9", "?sort=opus&page=1"],
  ])("%s → %s (쿼리 보존)", async (from, pathname, search) => {
    renderApp(from);
    await waitFor(() => expect(readLocation().pathname).toBe(pathname));
    expect(decodeURIComponent(readLocation().search)).toBe(decodeURIComponent(search));
  });

  it("관리 경로는 구분 밖 그대로다 — 리다이렉트하지 않는다 (기획 04 §3-6)", async () => {
    renderApp("/admin/works", "admin");
    await waitFor(() => expect(readLocation().pathname).toBe("/admin/works"));
  });

  it("곡 상세의 옛 주소는 '구분을 모를 때 쓰는 주소'다 — 응답의 section 으로 자기 교정한다 (기획 04 §1-4)", async () => {
    mockFetch([
      { url: /\/api\/works\/\d+(\?|$)/, data: { ...workDetail(), section: "VIOLIN" } },
      { url: /\/api\/works\/popular/, data: [] },
    ]);
    renderWithProviders(<App />, { route: "/works/21" });
    await waitFor(() => expect(readLocation().pathname).toBe("/violin/works/21"));
  });
});

describe("구분 경로가 화면을 연다", () => {
  it.each([
    ["/piano", "쉬운악보"],
    ["/piano/search?q=녹턴", "검색 결과"],
    ["/piano/works/21", "월광 소나타"],
    ["/piano/composers", "작곡가"],
    ["/piano/composers/9", "쇼팽"],
  ])("%s 는 그 화면을 그린다", async (route, text) => {
    renderApp(route);
    await findText(text);
  });

  it("준비 중 구분은 안내 화면을 연다 — 빈 목록도 404 도 아니다 (인수 조건 8-A 4)", async () => {
    renderApp("/violin");
    await findText("바이올린 악보는 아직 준비 중이에요");
  });

  it.each(["/cello", "/cello/works/3", "/violin/search"])(
    "%s 는 찾을 수 없는 페이지다 (인수 조건 8-B 6)",
    async (route) => {
      renderApp(route);
      await findText("찾을 수 없는 페이지");
    },
  );

  it("찾을 수 없는 페이지의 보조 문구가 구분을 언급한다 (08 §3)", async () => {
    renderApp("/cello");
    await findText("주소가 틀렸거나, 내려간 곡이거나, 아직 열지 않은 악기 구분이에요");
  });
});

describe("브라우저 탭 제목 (08 §5 D5)", () => {
  it.each([
    ["/piano", "쉬운악보 — 피아노"],
    ["/piano/search?q=녹턴", "'녹턴' 검색 결과 — 쉬운악보 피아노"],
    ["/piano/works/21", "월광 소나타 — 쉬운악보 피아노"],
    ["/piano/composers", "작곡가 — 쉬운악보 피아노"],
    ["/piano/composers/9", "쇼팽 — 쉬운악보 피아노"],
    ["/violin", "바이올린 준비 중 — 쉬운악보"],
    ["/cello", "찾을 수 없는 페이지 — 쉬운악보"],
  ])("%s → %s", async (route, title) => {
    renderApp(route);
    await waitFor(() => expect(document.title).toBe(title));
  });

  it("관리 화면에는 구분 이름이 붙지 않는다", async () => {
    renderApp("/admin", "admin");
    await waitFor(() => expect(document.title).toBe("관리 — 쉬운악보"));
  });
});
