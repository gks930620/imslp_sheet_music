import { screen } from "@testing-library/react";
import { Route } from "react-router-dom";
import { LoginPage } from "./LoginPage.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";

// 00_공통_레이아웃_토큰.md §8: 로그인 `/login?redirect=` (기존 화면에 복귀 파라미터만 추가)
// 기획 §6: 비로그인으로 관리 주소 진입 → 로그인 후 원래 관리 화면 / 홈에서 로그인 → 홈

function renderLogin(route) {
  return renderWithProviders(<LoginPage />, {
    route,
    path: "/login",
    auth: "admin", // 이미 인증됨 → LoginPage 는 목적지로 이동한다
    extraRoutes: [
      <Route key="admin" path="/admin/works" element={<p>관리 곡 목록</p>} />,
      <Route key="home" path="/" element={<p>홈</p>} />,
    ],
  });
}

describe("LoginPage — 로그인 후 복귀", () => {
  it("?redirect= 가 있으면 그 주소(쿼리 포함)로 돌아간다", () => {
    const { getLocation } = renderLogin("/login?redirect=%2Fadmin%2Fworks%3Fstatus%3DHIDDEN");
    expect(screen.getByText("관리 곡 목록")).toBeInTheDocument();
    expect(getLocation().pathname).toBe("/admin/works");
    expect(getLocation().params.get("status")).toBe("HIDDEN");
  });

  it("redirect 가 없으면 홈으로", () => {
    const { getLocation } = renderLogin("/login");
    expect(screen.getByText("홈")).toBeInTheDocument();
    expect(getLocation().pathname).toBe("/");
  });
});
