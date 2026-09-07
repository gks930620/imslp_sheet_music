import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useState } from "react";
import { useAuth } from "../context/AuthContext.jsx";
import { SearchBar } from "./sheetmusic/SearchBar.jsx";
import { AdminBanner } from "./sheetmusic/admin/AdminBanner.jsx";

const IMSLP_URL = "https://imslp.org/";

function classNames(...values) {
  return values.filter(Boolean).join(" ");
}

export function Layout() {
  const { user, status, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    setMobileOpen(false);
    navigate("/");
  };

  const isAdmin = Boolean(user?.roles?.includes("ADMIN"));
  const isComposerActive = location.pathname.startsWith("/composers");
  const isAdminActive = location.pathname.startsWith("/admin");
  // 홈·검색 결과에는 본문에 큰 검색창이 있으므로 헤더 검색창을 숨긴다 (00 §2-1)
  const showHeaderSearch = location.pathname !== "/" && location.pathname !== "/search";

  return (
    <div className="app-root">
      <header className="app-header">
        <div className="header-container">
          <NavLink className="header-logo" to="/">
            <span className="material-icons">piano</span>
            <span>쉬운악보</span>
          </NavLink>

          {showHeaderSearch ? (
            <div className="header-search">
              <SearchBar variant="compact" initialValue={searchParams.get("q") ?? ""} />
            </div>
          ) : null}

          <nav className={classNames("header-nav", mobileOpen && "mobile-open")} id="headerNav">
            <NavLink
              className={classNames("nav-item", isComposerActive && "active")}
              to="/composers"
              onClick={() => setMobileOpen(false)}
            >
              <span className="material-icons">people</span>
              <span>작곡가</span>
            </NavLink>
            {isAdmin ? (
              <NavLink
                className={classNames("nav-item", isAdminActive && "active")}
                to="/admin"
                onClick={() => setMobileOpen(false)}
              >
                <span className="material-icons">admin_panel_settings</span>
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
                  <span className="material-icons">person</span>
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
                <span className="material-icons">login</span>
                <span>로그인</span>
              </NavLink>
            )}
          </div>

          <button className="mobile-menu-btn" type="button" aria-label="메뉴" onClick={() => setMobileOpen((prev) => !prev)}>
            <span className="material-icons">{mobileOpen ? "close" : "menu"}</span>
          </button>
        </div>
      </header>

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
