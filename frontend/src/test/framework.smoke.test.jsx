import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route } from "react-router-dom";
import { Layout } from "../components/Layout.jsx";
import { ProtectedRoute } from "../components/ProtectedRoute.jsx";
import { renderWithProviders } from "./renderWithProviders.jsx";
import { mockFetch, findCall } from "./apiMock.js";

// 테스트 프레임워크 자체(jsdom·jest-dom·AuthContext 목·라우터 프로브·fetch 목)가 동작하는지 확인하는 스모크.
// 기존 코드로 통과해야 한다 — 이 파일이 실패하면 새 Red 테스트의 실패 원인을 믿을 수 없다.
describe("테스트 프레임워크 스모크", () => {
  it("AuthContext 목: admin 이면 헤더에 닉네임·로그아웃이 보인다", () => {
    renderWithProviders(<Layout />, { auth: "admin" });
    expect(screen.getByText("관리자")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그아웃" })).toBeInTheDocument();
  });

  it("AuthContext 목: guest 면 로그인 링크가 보인다", () => {
    renderWithProviders(<Layout />, { auth: "guest" });
    expect(screen.getByRole("link", { name: /로그인/ })).toHaveAttribute("href", "/login");
  });

  it("라우터 프로브: ProtectedRoute 가 비로그인을 /login 으로 보내고 state.from 을 남긴다", () => {
    const { getLocation } = renderWithProviders(
      <ProtectedRoute>
        <p>비밀</p>
      </ProtectedRoute>,
      // 라우트 가드는 반드시 path 로 감싼다 — URL 이 /login 으로 바뀐 뒤에도 가드가 남아 있으면 Navigate 가 무한 반복된다.
      {
        route: "/mypage?tab=1",
        path: "/mypage",
        auth: "guest",
        extraRoutes: [<Route key="login" path="/login" element={<p>로그인 화면</p>} />],
      },
    );
    expect(screen.getByText("로그인 화면")).toBeInTheDocument();
    expect(screen.queryByText("비밀")).not.toBeInTheDocument();
    expect(getLocation().pathname).toBe("/login");
    expect(getLocation().state).toEqual({ from: "/mypage?tab=1" });
  });

  it("fetch 목: 규칙에 맞는 응답을 돌려주고 호출을 기록한다", async () => {
    mockFetch([{ url: "/api/ping", data: { pong: true } }]);
    const res = await fetch("/api/ping?x=1");
    expect(res.ok).toBe(true);
    expect((await res.json()).data).toEqual({ pong: true });
    expect(findCall("/api/ping").params.get("x")).toBe("1");
  });

  it("user-event: 입력이 동작한다", async () => {
    const user = userEvent.setup();
    renderWithProviders(<input aria-label="입력" />);
    await user.type(screen.getByLabelText("입력"), "월광");
    expect(screen.getByLabelText("입력")).toHaveValue("월광");
  });
});
