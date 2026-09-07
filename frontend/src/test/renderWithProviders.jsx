import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { setAuth } from "./authMock.js";

/**
 * MemoryRouter + AuthContext 목으로 감싸 렌더한다.
 *
 * @param ui            렌더할 엘리먼트
 * @param options.route 초기 URL (기본 "/")
 * @param options.path  라우트 패턴. 주면 <Routes><Route path element={ui}/></Routes> 로 감싸 useParams 가 동작한다
 * @param options.auth  "guest" | "loading" | "user" | "admin" | 직접 만든 useAuth 반환값
 * @param options.extraRoutes  추가 <Route> 배열 (예: 이동 대상 화면 확인용)
 * @returns RTL 결과 + getLocation(): { pathname, search, state, params: URLSearchParams }
 */
export function renderWithProviders(ui, { route = "/", path, auth = "guest", extraRoutes = [] } = {}) {
  setAuth(auth);

  let body;
  if (path) {
    body = (
      <Routes>
        <Route path={path} element={ui} />
        {extraRoutes}
      </Routes>
    );
  } else if (extraRoutes.length) {
    body = (
      <Routes>
        <Route path="*" element={ui} />
        {extraRoutes}
      </Routes>
    );
  } else {
    body = ui;
  }

  const result = render(
    <MemoryRouter initialEntries={[route]}>
      <LocationProbe />
      {body}
    </MemoryRouter>,
  );
  return { ...result, getLocation: readLocation };
}

function LocationProbe() {
  const location = useLocation();
  return (
    <div
      hidden
      data-testid="location-probe"
      data-pathname={location.pathname}
      data-search={location.search}
      data-state={JSON.stringify(location.state ?? null)}
    />
  );
}

export function readLocation() {
  const el = document.querySelector('[data-testid="location-probe"]');
  const pathname = el?.dataset.pathname ?? "";
  const search = el?.dataset.search ?? "";
  return {
    pathname,
    search,
    state: JSON.parse(el?.dataset.state ?? "null"),
    params: new URLSearchParams(search),
  };
}
