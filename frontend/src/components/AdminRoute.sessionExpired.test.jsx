import { act, screen } from "@testing-library/react";
import { Route } from "react-router-dom";
import { AdminRoute } from "./AdminRoute.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { expectNoText, expectText } from "../test/text.js";

// 00_공통_레이아웃_토큰.md §4 "세션 만료(관리)" / 05_관리자_… 공통 표 "세션 만료(저장 시)"
//
// 관리 화면을 보고 있는 동안 세션이 풀리면(토큰 재발급 실패) 조용히 로그인 화면으로 튕기지 않는다.
//   InlineAlert danger `로그인이 풀렸어요. 다시 로그인해 주세요` + `작성 중이던 내용은 저장되지 않았어요`
//   → 2초 후 /login?redirect={원래 경로}
//
// 신호는 이미 있다: lib/http.js 가 refresh 실패 시 window 에 `auth:expired` 를 발행하고
// AuthContext 가 게스트로 강등한다. 안내를 얹는 자리는 **관리 화면 공통 가드인 AdminRoute** 다
// (관리 화면마다 붙이면 새 화면에서 빠진다. 05 공통 표가 요구하는 것도 화면별이 아니라 공통 동작이다).
//
// 주의(구현 순서): AdminRoute 는 만료 안내를 `!isAuthenticated → <Navigate>` 보다 **먼저** 판단해야 한다.
// AuthContext 강등이 같은 순간에 일어나므로, 순서가 뒤바뀌면 안내 없이 곧바로 이동해 버린다.

function renderGuard(route = "/admin/works/12") {
  return renderWithProviders(
    <AdminRoute>
      <p>관리 본문</p>
    </AdminRoute>,
    {
      route,
      path: "/admin/*",
      auth: "admin",
      extraRoutes: [<Route key="login" path="/login" element={<p>로그인 화면</p>} />],
    },
  );
}

function expireSession() {
  act(() => {
    window.dispatchEvent(new CustomEvent("auth:expired"));
  });
}

describe("AdminRoute — 세션 만료 안내", () => {
  it("만료 전에는 안내가 없다", () => {
    renderGuard();
    expectNoText("로그인이 풀렸어요");
  });

  it("세션이 풀리면 danger 안내 두 줄을 띄우고, 바로 이동하지는 않는다", () => {
    vi.useFakeTimers();
    const { getLocation } = renderGuard();

    expireSession();

    expectText("로그인이 풀렸어요. 다시 로그인해 주세요");
    expectText("작성 중이던 내용은 저장되지 않았어요");
    expect(document.querySelector(".inline-alert-danger")).not.toBeNull();
    expect(getLocation().pathname).toBe("/admin/works/12");
    expect(screen.queryByText("로그인 화면")).not.toBeInTheDocument();
  });

  it("2초 뒤 /login?redirect={원래 경로(쿼리 포함)} 로 이동한다", () => {
    vi.useFakeTimers();
    const { getLocation } = renderGuard("/admin/works?status=HIDDEN");

    expireSession();
    act(() => {
      vi.advanceTimersByTime(2000);
    });

    expect(screen.getByText("로그인 화면")).toBeInTheDocument();
    expect(getLocation().pathname).toBe("/login");
    expect(getLocation().params.get("redirect")).toBe("/admin/works?status=HIDDEN");
  });
});
