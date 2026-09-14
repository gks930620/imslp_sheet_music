import { screen, within, waitFor } from "@testing-library/react";
import { Layout } from "./Layout.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { mockFetch, findCall } from "../test/apiMock.js";
import { crawlJob } from "../test/fixtures.js";
import { expectNoText, findText } from "../test/text.js";

// 00_공통_레이아웃_토큰.md §2-1 헤더, §2-2 푸터, §3-14 AdminBanner

function header() {
  return within(screen.getByRole("banner"));
}

describe("Layout 헤더", () => {
  beforeEach(() => {
    mockFetch([{ url: "/api/admin/crawl/jobs/active", data: null }]);
  });

  it("로고 '쉬운악보' 는 홈으로 간다", () => {
    renderWithProviders(<Layout />);
    expect(header().getByRole("link", { name: /쉬운악보/ })).toHaveAttribute("href", "/piano");
    expect(header().queryByText("Boilerplate")).not.toBeInTheDocument();
  });

  it("'작곡가' 메뉴가 있고, 커뮤니티·채팅 메뉴는 없다", () => {
    renderWithProviders(<Layout />);
    expect(header().getByRole("link", { name: /작곡가/ })).toHaveAttribute("href", "/piano/composers");
    expect(header().queryByText("커뮤니티")).not.toBeInTheDocument();
    expect(header().queryByText("채팅")).not.toBeInTheDocument();
  });

  it("ADMIN 에게만 '관리' 메뉴가 보인다", () => {
    renderWithProviders(<Layout />, { auth: "admin" });
    expect(header().getByRole("link", { name: /관리/ })).toHaveAttribute("href", "/admin");
  });

  it("USER 에게는 '관리' 메뉴가 없다", () => {
    renderWithProviders(<Layout />, { auth: "user" });
    expect(header().queryByRole("link", { name: /^관리$/ })).not.toBeInTheDocument();
  });

  it("비로그인이면 '로그인' 이 보이고 로그아웃은 없다", () => {
    renderWithProviders(<Layout />, { auth: "guest" });
    expect(header().getByRole("link", { name: /로그인/ })).toHaveAttribute("href", "/login");
    expect(header().queryByRole("button", { name: "로그아웃" })).not.toBeInTheDocument();
  });

  it("홈(/)에서는 헤더 검색창을 숨긴다", () => {
    renderWithProviders(<Layout />, { route: "/" });
    expect(screen.queryByPlaceholderText("곡 이름, 작곡가, 작품번호")).not.toBeInTheDocument();
  });

  it("홈 이외 화면에서는 헤더 검색창(compact)이 보인다", () => {
    renderWithProviders(<Layout />, { route: "/works/21" });
    expect(header().getByPlaceholderText("곡 이름, 작곡가, 작품번호")).toBeInTheDocument();
  });

  it("작곡가 화면에서는 '작곡가' 메뉴가 active", () => {
    renderWithProviders(<Layout />, { route: "/composers/9" });
    expect(header().getByRole("link", { name: /작곡가/ })).toHaveClass("active");
  });
});

describe("Layout 푸터", () => {
  beforeEach(() => {
    mockFetch([]);
  });

  it("악보 출처·저작권 안내·서비스 이름 3줄, 연락처·저작권 표시(©) 없음", () => {
    renderWithProviders(<Layout />);
    const footer = within(screen.getByRole("contentinfo"));
    const source = footer.getByRole("link", { name: /악보 출처: IMSLP/ });
    expect(source).toHaveAttribute("href", expect.stringMatching(/^https:\/\/imslp\.org/));
    expect(source).toHaveAttribute("target", "_blank");
    expect(footer.getByText("저작권 안내: 각 악보의 한국 기준 이용 가능 여부는 판본별로 표시합니다.")).toBeInTheDocument();
    expect(footer.getByText("쉬운악보")).toBeInTheDocument();
    expectNoText("All rights reserved");
    expectNoText("연락처");
  });
});

describe("Layout — 관리자 수집 진행 띠(AdminBanner)", () => {
  it("ADMIN 이 관리 화면에 있고 진행 중 작업이 있으면 띠가 보인다", async () => {
    mockFetch([{ url: "/api/admin/crawl/jobs/active", data: crawlJob() }]);
    renderWithProviders(<Layout />, { route: "/admin/works", auth: "admin" });
    await findText("수집 진행 중 12/50");
    expect(screen.getByRole("link", { name: "보기" })).toHaveAttribute("href", "/admin/crawl/12");
  });

  it("관리 화면이 아니면 진행 중 작업을 조회하지 않는다", async () => {
    mockFetch([{ url: "/api/admin/crawl/jobs/active", data: crawlJob() }]);
    renderWithProviders(<Layout />, { route: "/", auth: "admin" });
    await waitFor(() => expect(screen.getByRole("banner")).toBeInTheDocument());
    expect(findCall(/\/api\/admin\/crawl\/jobs\/active/)).toBeUndefined();
    expectNoText("수집 진행 중");
  });
});
