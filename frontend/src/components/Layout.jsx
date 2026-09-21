import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext.jsx";
import { useSection, useSectionPath } from "../hooks/useSection.js";
import { peekLoginIntent } from "../lib/loginIntent.js";
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
  const { user, status, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [mobileOpen, setMobileOpen] = useState(false);
  const section = useSection();
  const sectionPath = useSectionPath();
  const jumped = useRef(false);

  /**
   * 소셜 로그인 왕복 복귀 — 03_기술결정 §22-2. 카카오·구글은 쿠키를 심고 **`/` 로** 떨어뜨려서
   * 로그인 화면의 `?redirect=` 가 살아남지 못한다. 앱이 뜬 뒤 **인증이 확정된 첫 순간 한 번만**,
   * 의도가 있고 만료 전이며 지금 주소가 다르면 그 주소로 옮긴다.
   *
   * 옮기는 것은 **주소뿐**이다 — 즐겨찾기 실행은 곡 상세가 한다(두 곳에서 실행하지 않는다).
   * 그래서 여기서는 take 가 아니라 peek 다. 아이디·비밀번호 로그인은 LoginPage 가 이미 보내므로 no-op 다.
   */
  useEffect(() => {
    if (jumped.current || status === "loading") return;
    jumped.current = true;
    if (!isAuthenticated) return;
    const intent = peekLoginIntent();
    if (!intent?.returnTo || intent.returnTo === location.pathname + location.search) return;
    navigate(intent.returnTo, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, isAuthenticated]);

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
  // 02 §10-4 — 경로가 `/{구분}/library` 로 시작하면 두 탭 모두 "내 악보" 활성
  const isLibraryActive = relativePath.startsWith("/library");
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
                {/* 00 §2-1(2026-09-20) — 이전 "마이페이지" 자리. 로그인 사용자에게만 보이고, 현재 구분의 즐겨찾기 탭으로 간다 */}
                <NavLink
                  className={classNames("nav-item", isLibraryActive && "active")}
                  to={sectionPath("/library/favorites")}
                  onClick={() => setMobileOpen(false)}
                >
                  <span className="material-icons" aria-hidden="true">library_music</span>
                  <span>내 악보</span>
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
