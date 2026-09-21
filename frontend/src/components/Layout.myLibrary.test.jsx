import { screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { Layout } from "./Layout.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { mockFetch } from "../test/apiMock.js";

/**
 * 헤더의 "내 악보" 와 소셜 로그인 복귀 — 00_공통 §2-1(2026-09-20), 기획 05 §2-1·§5-4,
 * 인수 조건 8-C 1 · 8-F 2·5 · 8-G 7. 계약: 02 §10-4(주소·활성 판정) · 03_기술결정 §22(복귀 점프).
 */

const LOGIN_INTENT_KEY = "sheetmusic.loginIntent";
const MY_LIBRARY_HREF = "/piano/library/favorites";

function header() {
  return within(screen.getByRole("banner"));
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
  mockFetch([{ url: "/api/admin/crawl/jobs/active", data: null }]);
});

describe("헤더 '내 악보' (8-C 1, 8-G 7)", () => {
  it("로그인 사용자에게 '내 악보' 가 보이고 '마이페이지' 는 없다", () => {
    renderWithProviders(<Layout />, { auth: "user", route: "/piano/works/21" });

    expect(header().getByRole("link", { name: /내 악보/ })).toHaveAttribute("href", MY_LIBRARY_HREF);
    expect(header().queryByText("마이페이지")).not.toBeInTheDocument();
  });

  it("비로그인 헤더에는 '내 악보' 가 없다", () => {
    renderWithProviders(<Layout />, { auth: "guest", route: "/piano/works/21" });

    expect(header().queryByRole("link", { name: /내 악보/ })).not.toBeInTheDocument();
  });

  it("관리자에게도 같은 메뉴가 있다 — 계정 기능은 역할과 무관하다 (기획 05 §2-1 7)", () => {
    renderWithProviders(<Layout />, { auth: "admin", route: "/piano" });

    expect(header().getByRole("link", { name: /내 악보/ })).toHaveAttribute("href", MY_LIBRARY_HREF);
  });

  it("두 탭 어디에 있든 '내 악보' 가 활성 표시다 (02 §10-4 — /{구분}/library 로 시작하면)", () => {
    const view = renderWithProviders(<Layout />, { auth: "user", route: "/piano/library/favorites" });
    expect(header().getByRole("link", { name: /내 악보/ })).toHaveClass("active");
    view.unmount();

    renderWithProviders(<Layout />, { auth: "user", route: "/piano/library/downloads" });
    expect(header().getByRole("link", { name: /내 악보/ })).toHaveClass("active");
  });

  it("다른 화면에서는 활성 표시가 아니다", () => {
    renderWithProviders(<Layout />, { auth: "user", route: "/piano/composers" });

    expect(header().getByRole("link", { name: /내 악보/ })).not.toHaveClass("active");
  });

  it("준비 중 구분에서도 '내 악보' 는 피아노 내 악보로 간다 (기획 05 §5-4·5-7)", () => {
    renderWithProviders(<Layout />, { auth: "user", route: "/violin" });

    expect(header().getByRole("link", { name: /내 악보/ })).toHaveAttribute("href", MY_LIBRARY_HREF);
  });
});

describe("소셜 로그인 왕복 복귀 — 주소만 옮긴다 (03 §22-2, 8-B 5)", () => {
  function givenIntent(intent) {
    sessionStorage.setItem(LOGIN_INTENT_KEY, JSON.stringify({ at: Date.now(), ...intent }));
  }

  it("카카오·구글이 '/' 로 떨어뜨려도 원래 보던 곡 상세로 돌아간다", async () => {
    givenIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });
    const { getLocation } = renderWithProviders(<Layout />, { auth: "user", route: "/piano" });

    await waitFor(() => expect(getLocation().pathname).toBe("/piano/works/21"));
    // 의도는 곡 상세가 소비한다 — 점프는 주소만 옮긴다(두 곳에서 실행하지 않는다)
    expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).not.toBeNull();
  });

  it("내 악보로 들어오려던 사람은 그 탭으로 돌아간다 (8-C 8)", async () => {
    givenIntent({ action: null, workId: null, returnTo: "/piano/library/downloads" });
    const { getLocation } = renderWithProviders(<Layout />, { auth: "user", route: "/piano" });

    await waitFor(() => expect(getLocation().pathname).toBe("/piano/library/downloads"));
  });

  it("의도가 없으면 아무 데도 가지 않는다 — 평소 이동을 가로채지 않는다", async () => {
    const { getLocation } = renderWithProviders(<Layout />, { auth: "user", route: "/piano" });

    await waitFor(() => expect(getLocation().pathname).toBe("/piano"));
  });

  it("비로그인이면 점프하지 않는다 — 로그인 화면에서 돌아가기를 누른 경우 (8-B 6)", async () => {
    givenIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });
    const { getLocation } = renderWithProviders(<Layout />, { auth: "guest", route: "/piano" });

    await waitFor(() => expect(getLocation().pathname).toBe("/piano"));
  });

  it("10분이 지난 의도로는 점프하지 않는다", async () => {
    givenIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21", at: Date.now() - 11 * 60 * 1000 });
    const { getLocation } = renderWithProviders(<Layout />, { auth: "user", route: "/piano" });

    await waitFor(() => expect(getLocation().pathname).toBe("/piano"));
  });

  it("이미 그 주소에 있으면 점프하지 않는다", async () => {
    givenIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });
    const { getLocation } = renderWithProviders(<Layout />, { auth: "user", route: "/piano/works/21" });

    await waitFor(() => expect(getLocation().pathname).toBe("/piano/works/21"));
  });
});
