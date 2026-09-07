import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HomePage } from "./HomePage.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { mockFetch, fakeResponse, findCall, findCalls } from "../test/apiMock.js";
import { popularWorks, featuredComposers } from "../test/fixtures.js";
import { expectNoText, expectText, findText } from "../test/text.js";

// 01_홈.md — 검색창 하나가 주인공. 인기곡 10 · 작곡가 8 은 독립 요청, 각각 실패 처리.
const PLACEHOLDER = "곡 이름, 작곡가, 작품번호로 찾기 — 예: 월광, 쇼팽 녹턴, K.545";

function mockHome({ popular = popularWorks(10), composers = featuredComposers(8) } = {}) {
  return mockFetch([
    { url: "/api/works/popular", data: popular },
    { url: "/api/composers/featured", data: composers },
  ]);
}

describe("HomePage — 기본", () => {
  it("서비스 이름·한 줄 설명, 보일러플레이트 흔적 없음", async () => {
    mockHome();
    renderWithProviders(<HomePage />);
    expect(screen.getByRole("heading", { level: 1, name: "쉬운악보" })).toBeInTheDocument();
    expect(screen.getByText("한국어로 검색하고 바로 받는 피아노 악보")).toBeInTheDocument();
    expectNoText("Spring Boot Boilerplate");
    expectNoText("커뮤니티 바로가기");
    await findText("인기곡 1");
  });

  it("검색창(large)이 있고 화면이 열리면 커서가 검색창에 있다", async () => {
    mockHome();
    renderWithProviders(<HomePage />);
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveFocus();
    await findText("인기곡 1");
  });

  it("예시 칩 3개: 월광 · 쇼팽 녹턴 · K.545 — 누르면 그 검색어로 검색", async () => {
    const user = userEvent.setup();
    mockHome();
    const { getLocation } = renderWithProviders(<HomePage />);
    expectText("예:");
    expect(screen.getByText("월광")).toBeInTheDocument();
    expect(screen.getByText("K.545")).toBeInTheDocument();
    await user.click(screen.getByText("쇼팽 녹턴"));
    expect(getLocation().pathname).toBe("/search");
    expect(getLocation().params.get("q")).toBe("쇼팽 녹턴");
  });

  it("검색창에 입력 후 Enter → /search?q=", async () => {
    const user = userEvent.setup();
    mockHome();
    const { getLocation } = renderWithProviders(<HomePage />);
    await user.type(screen.getByPlaceholderText(PLACEHOLDER), "엘리제{Enter}");
    expect(getLocation().pathname).toBe("/search");
    expect(getLocation().params.get("q")).toBe("엘리제");
  });

  it("빈 검색어 Enter → 이동 없음", async () => {
    const user = userEvent.setup();
    mockHome();
    const { getLocation } = renderWithProviders(<HomePage />);
    await user.type(screen.getByPlaceholderText(PLACEHOLDER), "{Enter}");
    expect(getLocation().pathname).toBe("/");
  });
});

describe("HomePage — 인기곡", () => {
  it("GET /api/works/popular?limit=10 을 부르고 10곡을 순위와 함께 보여준다", async () => {
    mockHome();
    renderWithProviders(<HomePage />);
    await findText("인기곡 10");
    expect(findCall("/api/works/popular").params.get("limit")).toBe("10");
    expect(screen.getByRole("heading", { name: "인기곡" })).toBeInTheDocument();
    const links = screen.getAllByRole("link").filter((a) => /^\/works\/\d+$/.test(a.getAttribute("href")));
    expect(links).toHaveLength(10);
    expect(links[0]).toHaveAttribute("href", "/works/100");
    expect(links[0]).toHaveTextContent("1");
    expect(links[0]).toHaveTextContent("인기곡 1");
  });

  it("인기곡 0개 → '아직 등록된 곡이 없어요' (제목은 유지)", async () => {
    mockHome({ popular: [] });
    renderWithProviders(<HomePage />);
    await findText("아직 등록된 곡이 없어요");
    expect(screen.getByRole("heading", { name: "인기곡" })).toBeInTheDocument();
  });

  it("인기곡 실패 → 인기곡 영역에만 '연결을 확인해 주세요' + '다시 시도', 작곡가는 정상", async () => {
    mockFetch([
      { url: "/api/works/popular", reject: true },
      { url: "/api/composers/featured", data: featuredComposers(8) },
    ]);
    renderWithProviders(<HomePage />);
    await findText("연결을 확인해 주세요");
    expect(screen.getAllByText("연결을 확인해 주세요")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    await findText("쇼팽");
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toBeInTheDocument();
  });

  it("'다시 시도' 를 누르면 인기곡을 다시 요청한다", async () => {
    const user = userEvent.setup();
    let calls = 0;
    mockFetch([
      {
        url: "/api/works/popular",
        handler: () => {
          calls += 1;
          if (calls === 1) throw new TypeError("Failed to fetch");
          return fakeResponse({ success: true, message: "성공", data: popularWorks(10) });
        },
      },
      { url: "/api/composers/featured", data: featuredComposers(8) },
    ]);
    renderWithProviders(<HomePage />);
    await findText("연결을 확인해 주세요");
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("인기곡 1");
    expect(findCalls("/api/works/popular")).toHaveLength(2);
  });
});

describe("HomePage — 작곡가 바로가기", () => {
  it("GET /api/composers/featured?limit=8 을 부르고 8명을 'N곡' 과 함께 보여준다", async () => {
    mockHome();
    renderWithProviders(<HomePage />);
    await findText("쇼팽");
    expect(findCall("/api/composers/featured").params.get("limit")).toBe("8");
    const links = screen.getAllByRole("link").filter((a) => /^\/composers\/\d+$/.test(a.getAttribute("href")));
    expect(links).toHaveLength(8);
    expect(links[0]).toHaveTextContent("쇼팽");
    expect(links[0]).toHaveTextContent("24곡");
  });

  it("'모든 작곡가 보기' → /composers", async () => {
    mockHome();
    renderWithProviders(<HomePage />);
    expect(screen.getByRole("link", { name: /모든 작곡가 보기/ })).toHaveAttribute("href", "/composers");
    await findText("쇼팽");
  });

  it("작곡가 0명 → '아직 등록된 작곡가가 없어요', 링크는 그대로", async () => {
    mockHome({ composers: [] });
    renderWithProviders(<HomePage />);
    await findText("아직 등록된 작곡가가 없어요");
    expect(screen.getByRole("link", { name: /모든 작곡가 보기/ })).toBeInTheDocument();
  });

  it("작곡가 실패 → 작곡가 영역에만 오류, 인기곡은 정상", async () => {
    mockFetch([
      { url: "/api/works/popular", data: popularWorks(10) },
      { url: "/api/composers/featured", reject: true },
    ]);
    renderWithProviders(<HomePage />);
    await findText("연결을 확인해 주세요");
    await waitFor(() => expect(screen.getAllByText("연결을 확인해 주세요")).toHaveLength(1));
    await findText("인기곡 1");
  });
});
