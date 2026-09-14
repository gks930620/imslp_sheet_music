import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../context/AuthContext.jsx";
import { useSection, useSectionPath } from "../hooks/useSection.js";
import { SearchBar } from "./sheetmusic/SearchBar.jsx";
import { SectionTabs } from "./sheetmusic/SectionTabs.jsx";
import { AdminBanner } from "./sheetmusic/admin/AdminBanner.jsx";

const IMSLP_URL = "https://imslp.org/";

/**
 * 00 §2-0 · 08 §1-1 — 구분 바가 안 보이는 화면의 첫 세그먼트: 관리 전체 · 로그인 · 범위 밖 유지 화면(마이페이지·커뮤니티·채팅).
 * 경로로 판단해 렌더 자체를 하지 않는다 — CSS 로 숨기면 관리 화면에서 한 순간 깜빡인다(인수 조건 8-A 7).
 */
const OUT_OF_SECTION = new Set(["admin", "login", "signup", "mypage", "community", "rooms"]);

function classNames(...values) {
  return values.filter(Boolean).join(" ");
}

export function Layout() {
  const { user, status, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [mobileOpen, setMobileOpen] = useState(false);
  const section = useSection();
  const sectionPath = useSectionPath();

  const handleLogout = async () => {
    await logout();
    setMobileOpen(false);
    navigate(sectionPath());
  };

  const isAdmin = Boolean(user?.roles?.includes("ADMIN"));
  const firstSegment = location.pathname.split("/")[1] ?? "";
  // 구분 접두사를 뗀 나머지 경로 — "/piano/search" → "/search", "/piano" → ""
  const relativePath = section ? location.pathname.slice(section.slug.length + 1) : location.pathname;
  const isComposerActive = relativePath.startsWith("/composers");
  const isAdminActive = firstSegment === "admin";
  const showSectionTabs = !OUT_OF_SECTION.has(firstSegment);
  // 홈·검색 결과에는 본문에 큰 검색창이 있으므로 헤더 검색창을 숨긴다 (00 §2-1). 옛 주소(/, /search)는 곧 리다이렉트되지만 깜빡임을 막는다
  const isHomeOrSearch =
    (section?.open && (relativePath === "" || relativePath === "/" || relativePath === "/search")) ||
    location.pathname === "/" ||
    location.pathname === "/search";
  // 08 §2-4 — 준비 중 안내 화면의 헤더 검색만 from 을 붙인다(그 아래 하위 경로는 404 라 붙이지 않는다, 08 §3)
  const searchFrom = section && !section.open && (relativePath === "" || relativePath === "/") ? section.slug : undefined;

  return (
    <div className="app-root">
      <header className="app-header">
        <div className="header-container">
          <NavLink className="header-logo" to={sectionPath()}>
            <span className="material-icons" aria-hidden="true">piano</span>
            <span>쉬운악보</span>
          </NavLink>

          {!isHomeOrSearch ? (
            <div className="header-search">
              <SearchBar variant="compact" initialValue={searchParams.get("q") ?? ""} from={searchFrom} />
            </div>
          ) : null}

          <nav className={classNames("header-nav", mobileOpen && "mobile-open")} id="headerNav">
            <NavLink
              className={classNames("nav-item", isComposerActive && "active")}
              to={sectionPath("/composers")}
              onClick={() => setMobileOpen(false)}
            >
              <span className="material-icons" aria-hidden="true">people</span>
              <span>작곡가</span>
            </NavLink>
            {isAdmin ? (
              <NavLink
                className={classNames("nav-item", isAdminActive && "active")}
                to="/admin"
                onClick={() => setMobileOpen(false)}
              >
                <span className="material-icons" aria-hidden="true">admin_panel_settings</span>
                <span>관리</span>
              </NavLink>
            ) : null}
          </nav>

          <div className="user-menu">
            {status === "loading" && (
              <div className="auth-loading" id="authLoading">
                <span>...</span>
              </div>
            )}

            {status !== "loading" && user && (
              <>
                <NavLink className="nav-item" to="/mypage" onClick={() => setMobileOpen(false)}>
                  <span className="material-icons" aria-hidden="true">person</span>
                  <span>마이페이지</span>
                </NavLink>
                <div className="user-info">
                  <span className="material-icons">account_circle</span>
                  <span>{user.nickname}</span>
                </div>
                <button type="button" className="logout-btn" onClick={handleLogout}>
                  로그아웃
                </button>
              </>
            )}

            {status !== "loading" && !user && (
              <NavLink className="login-btn" to="/login" onClick={() => setMobileOpen(false)}>
                <span className="material-icons" aria-hidden="true">login</span>
                <span>로그인</span>
              </NavLink>
            )}
          </div>

          <button className="mobile-menu-btn" type="button" aria-label="메뉴" onClick={() => setMobileOpen((prev) => !prev)}>
            <span className="material-icons">{mobileOpen ? "close" : "menu"}</span>
          </button>
        </div>
      </header>

      {showSectionTabs ? <SectionTabs /> : null}

      {isAdmin && isAdminActive ? <AdminBanner /> : null}

      <main className="main-content">
        <Outlet />
      </main>

      <footer className="app-footer">
        <div className="footer-container">
          <p>
            <a href={IMSLP_URL} target="_blank" rel="noreferrer">
              악보 출처: IMSLP (imslp.org)
            </a>
          </p>
          <p>저작권 안내: 각 악보의 한국 기준 이용 가능 여부는 판본별로 표시합니다.</p>
          <p>쉬운악보</p>
        </div>
      </footer>
    </div>
  );
}
