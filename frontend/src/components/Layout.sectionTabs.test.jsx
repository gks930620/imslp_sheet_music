import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Layout } from "./Layout.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { mockFetch } from "../test/apiMock.js";

// 화면정의 08 §1-1(보이는 화면/안 보이는 화면) · §2-2(헤더 검색) · 기획 04 §3-6 · 인수 조건 8-A 1·7

function renderLayout(route, auth = "guest") {
  mockFetch([{ url: "/api/admin/crawl/jobs/active", data: null }]);
  return renderWithProviders(<Layout />, { route, auth });
}

function sectionTabs() {
  return screen.queryByRole("navigation", { name: "악기 구분" });
}

describe("구분 바가 보이는 화면 / 안 보이는 화면", () => {
  it.each([
    "/piano",
    "/piano/search?q=녹턴",
    "/piano/works/21",
    "/piano/composers",
    "/piano/composers/9",
    "/violin",
    "/orchestra",
    "/cello",
  ])("%s — 일반 사용자 화면에는 구분 바가 있다 (인수 조건 8-A 1)", (route) => {
    renderLayout(route);
    expect(sectionTabs()).toBeInTheDocument();
  });

  it.each(["/admin", "/admin/works", "/admin/works/21", "/admin/copyright", "/admin/crawl/12"])(
    "%s — 관리 화면에는 구분 바를 렌더하지 않는다 (인수 조건 8-A 7, 08 §7 F3)",
    (route) => {
      renderLayout(route, "admin");
      expect(sectionTabs()).not.toBeInTheDocument();
    },
  );

  it("로그인 화면에도 구분 바가 없다 (08 §1-1)", () => {
    renderLayout("/login");
    expect(sectionTabs()).not.toBeInTheDocument();
  });
});

describe("헤더는 구분을 따라간다", () => {
  it("로고는 현재 구분의 홈으로 간다", () => {
    renderLayout("/piano/works/21");
    expect(screen.getByRole("link", { name: /쉬운악보/ })).toHaveAttribute("href", "/piano");
  });

  it("구분이 아닌 주소(관리 화면)에서는 기본 구분의 홈으로 간다", () => {
    renderLayout("/admin/works", "admin");
    expect(screen.getByRole("link", { name: /쉬운악보/ })).toHaveAttribute("href", "/piano");
  });

  it("'작곡가' 메뉴는 현재 구분의 작곡가 목록으로 간다 (기획 04 §3-2)", () => {
    renderLayout("/piano/works/21");
    expect(screen.getByRole("link", { name: /작곡가/ })).toHaveAttribute("href", "/piano/composers");
  });

  it("'관리' 메뉴는 구분 밖 그대로다 (기획 04 §3-6)", () => {
    renderLayout("/piano", "admin");
    expect(screen.getByRole("link", { name: /^관리$/ })).toHaveAttribute("href", "/admin");
  });
});

describe("헤더 검색창 — 어디에 있고 어디로 가나", () => {
  it("홈·검색 결과에는 헤더 검색창이 없다 (00 §2-1) — 구분 경로에서도 같다", () => {
    renderLayout("/piano");
    expect(screen.queryByRole("search")).not.toBeInTheDocument();
  });

  it("검색 결과 화면에서도 헤더 검색창이 없다", () => {
    renderLayout("/piano/search?q=녹턴");
    expect(screen.queryByRole("search")).not.toBeInTheDocument();
  });

  it("준비 중 안내 화면에서는 헤더 검색창이 살아 있다 (08 §2-2)", () => {
    renderLayout("/violin");
    expect(screen.getByRole("search")).toBeInTheDocument();
  });

  it("준비 중 화면의 헤더 검색은 피아노 결과로 가고 from 을 실어 보낸다 (인수 조건 8-A 6)", async () => {
    const { getLocation } = renderLayout("/violin");
    await userEvent.type(screen.getByPlaceholderText("곡 이름, 작곡가, 작품번호"), "녹턴{Enter}");

    await waitFor(() => expect(getLocation().pathname).toBe("/piano/search"));
    expect(getLocation().params.get("q")).toBe("녹턴");
    expect(getLocation().params.get("from")).toBe("violin");
    expect(getLocation().params.has("in")).toBe(false);
  });

  it("찾을 수 없는 페이지의 검색은 피아노 결과로 가되 from 을 붙이지 않는다 (08 §3)", async () => {
    const { getLocation } = renderLayout("/cello");
    await userEvent.type(screen.getByPlaceholderText("곡 이름, 작곡가, 작품번호"), "녹턴{Enter}");

    await waitFor(() => expect(getLocation().pathname).toBe("/piano/search"));
    expect(getLocation().params.has("from")).toBe(false);
  });

  it("헤더 검색은 항상 '전체'로 간다 — 헤더에는 기준 선택이 없다 (기획 04 §4-5, 08 §4-6 D7)", async () => {
    const { getLocation } = renderLayout("/piano/works/21");
    expect(screen.queryByRole("radiogroup", { name: "검색 기준" })).not.toBeInTheDocument();

    await userEvent.type(screen.getByPlaceholderText("곡 이름, 작곡가, 작품번호"), "쇼팽{Enter}");
    await waitFor(() => expect(getLocation().pathname).toBe("/piano/search"));
    expect(getLocation().params.has("in")).toBe(false);
  });
});
