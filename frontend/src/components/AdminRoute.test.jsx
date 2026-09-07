import { screen } from "@testing-library/react";
import { Route } from "react-router-dom";
import { AdminRoute } from "./AdminRoute.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";

// 05_관리자_홈_및_작곡가곡관리.md 공통: 관리 화면 진입 (비로그인 → /login?redirect=, USER → AccessDenied, ADMIN 통과)
// 주의: 라우트 가드는 path 로 감싸야 한다 (URL 이 바뀐 뒤 가드가 남아 있으면 Navigate 무한 반복)

function renderGuard(route, auth) {
  return renderWithProviders(
    <AdminRoute>
      <p>관리 본문</p>
    </AdminRoute>,
    {
      route,
      path: "/admin/*",
      auth,
      extraRoutes: [<Route key="login" path="/login" element={<p>로그인 화면</p>} />],
    },
  );
}

describe("AdminRoute", () => {
  it("비로그인 → /login?redirect={원래 경로(쿼리 포함)} 로 보낸다", () => {
    const { getLocation } = renderGuard("/admin/works?status=HIDDEN", "guest");
    expect(screen.getByText("로그인 화면")).toBeInTheDocument();
    expect(screen.queryByText("관리 본문")).not.toBeInTheDocument();
    expect(getLocation().pathname).toBe("/login");
    expect(getLocation().params.get("redirect")).toBe("/admin/works?status=HIDDEN");
  });

  it("USER → 같은 주소에서 '관리자만 들어갈 수 있어요' + '홈으로'", () => {
    const { getLocation } = renderGuard("/admin", "user");
    expect(screen.getByText("관리자만 들어갈 수 있어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "홈으로" })).toHaveAttribute("href", "/");
    expect(screen.queryByText("관리 본문")).not.toBeInTheDocument();
    expect(getLocation().pathname).toBe("/admin");
  });

  it("ADMIN → 본문을 보여준다", () => {
    renderGuard("/admin", "admin");
    expect(screen.getByText("관리 본문")).toBeInTheDocument();
  });

  it("인증 확인 중이면 이동하지 않고 스피너만", () => {
    const { getLocation } = renderGuard("/admin", "loading");
    expect(screen.queryByText("관리 본문")).not.toBeInTheDocument();
    expect(screen.queryByText("로그인 화면")).not.toBeInTheDocument();
    expect(getLocation().pathname).toBe("/admin");
  });
});
