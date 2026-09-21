import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { Layout } from "./components/Layout.jsx";
import { ProtectedRoute } from "./components/ProtectedRoute.jsx";
import { AdminRoute } from "./components/AdminRoute.jsx";
import { HomePage } from "./pages/HomePage.jsx";
import { LoginPage } from "./pages/LoginPage.jsx";
import { SignupPage } from "./pages/SignupPage.jsx";
import { MyPage } from "./pages/MyPage.jsx";
import { CommunityListPage } from "./pages/CommunityListPage.jsx";
import { CommunityDetailPage } from "./pages/CommunityDetailPage.jsx";
import { CommunityWritePage } from "./pages/CommunityWritePage.jsx";
import { CommunityEditPage } from "./pages/CommunityEditPage.jsx";
import { RoomListPage } from "./pages/RoomListPage.jsx";
import { RoomPage } from "./pages/RoomPage.jsx";
import { NotFoundPage } from "./pages/NotFoundPage.jsx";
import { SearchResultPage } from "./pages/sheetmusic/SearchResultPage.jsx";
import { WorkDetailPage } from "./pages/sheetmusic/WorkDetailPage.jsx";
import { ComposerListPage } from "./pages/sheetmusic/ComposerListPage.jsx";
import { ComposerDetailPage } from "./pages/sheetmusic/ComposerDetailPage.jsx";
import { MyLibraryPage } from "./pages/sheetmusic/MyLibraryPage.jsx";
import { SectionPreparingPage } from "./pages/sheetmusic/SectionPreparingPage.jsx";
import { AdminDashboardPage } from "./pages/admin/AdminDashboardPage.jsx";
import { CrawlAdminPage } from "./pages/admin/CrawlAdminPage.jsx";
import { CrawlProgressPage } from "./pages/admin/CrawlProgressPage.jsx";
import { ComposerAdminListPage } from "./pages/admin/ComposerAdminListPage.jsx";
import { ComposerFormPage } from "./pages/admin/ComposerFormPage.jsx";
import { WorkAdminListPage } from "./pages/admin/WorkAdminListPage.jsx";
import { WorkFormPage } from "./pages/admin/WorkFormPage.jsx";
import { CopyrightPendingPage } from "./pages/admin/CopyrightPendingPage.jsx";
import { DEFAULT_SECTION, SECTIONS } from "./lib/sections.js";

/**
 * 기획 04 §3-5 · 02 §0-5 — 구분이 없던 시절의 주소는 같은 화면의 `/piano/…` 로 replace 리다이렉트(쿼리 보존).
 * 죽은 호환 코드가 아니라 "구분을 모를 때 쓰는 주소"다 — 관리 화면의 "사용자 화면에서 보기"(`/works/:id`)가 계속 쓴다.
 * 옛 경로는 접두사만 다르므로 pathname 을 그대로 옮긴다.
 */
function LegacyRedirect() {
  const { pathname, search } = useLocation();
  const suffix = pathname === "/" ? "" : pathname;
  return <Navigate to={{ pathname: `/${DEFAULT_SECTION.slug}${suffix}`, search }} replace />;
}

// 라우트 표: docs/화면정의/00_공통_레이아웃_토큰.md §8 · 02_API §0-5 (2026-09-10 구분 경로)
// 일반 화면은 전부 열린 구분(`/piano`) 아래. 준비 중 구분(`/violin`, `/orchestra`)은 안내 화면 하나뿐이고
// 그 하위 경로·없는 구분 이름(`/cello`)은 `*` 로 떨어져 찾을 수 없는 페이지다(03 §21-1).
// 관리 경로는 구분 밖 그대로, /admin 중첩 라우트 한 곳에서 AdminRoute 로 감싼다(새 관리 화면을 추가해도 가드가 빠지지 않게).
// 등록(/new)은 상세(/:id)보다 먼저 등록한다.
function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<LegacyRedirect />} />
        <Route path="/search" element={<LegacyRedirect />} />
        <Route path="/works/:id" element={<LegacyRedirect />} />
        <Route path="/composers" element={<LegacyRedirect />} />
        <Route path="/composers/:id" element={<LegacyRedirect />} />

        {SECTIONS.map((section) =>
          section.open ? (
            <Route key={section.slug} path={section.slug}>
              <Route index element={<HomePage />} />
              <Route path="search" element={<SearchResultPage />} />
              <Route path="works/:id" element={<WorkDetailPage />} />
              <Route path="composers" element={<ComposerListPage />} />
              <Route path="composers/:id" element={<ComposerDetailPage />} />
              {/* 02 §10-4 — 내 악보는 탭마다 자기 주소다. `/{구분}/library` 는 즐겨찾기 탭으로 replace(히스토리에 남기지 않는다).
                  탭 라우트는 **두 개뿐**이라 `/piano/library/xyz` 는 `*` 로 떨어져 찾을 수 없는 페이지가 된다(8-F 5). */}
              <Route path="library">
                <Route index element={<Navigate to="favorites" replace />} />
                <Route path="favorites" element={<MyLibraryPage />} />
                <Route path="downloads" element={<MyLibraryPage />} />
              </Route>
            </Route>
          ) : (
            <Route key={section.slug} path={section.slug} element={<SectionPreparingPage />} />
          ),
        )}

        <Route
          path="/admin"
          element={
            <AdminRoute>
              <Outlet />
            </AdminRoute>
          }
        >
          <Route index element={<AdminDashboardPage />} />
          <Route path="composers" element={<ComposerAdminListPage />} />
          <Route path="composers/new" element={<ComposerFormPage />} />
          <Route path="composers/:id" element={<ComposerFormPage />} />
          <Route path="works" element={<WorkAdminListPage />} />
          <Route path="works/new" element={<WorkFormPage />} />
          <Route path="works/:id" element={<WorkFormPage />} />
          <Route path="copyright" element={<CopyrightPendingPage />} />
          <Route path="crawl" element={<CrawlAdminPage />} />
          <Route path="crawl/:jobId" element={<CrawlProgressPage />} />
        </Route>

        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route
          path="/mypage"
          element={
            <ProtectedRoute>
              <MyPage />
            </ProtectedRoute>
          }
        />
        <Route path="/community" element={<CommunityListPage />} />
        <Route path="/community/detail" element={<CommunityDetailPage />} />
        <Route
          path="/community/write"
          element={
            <ProtectedRoute>
              <CommunityWritePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/community/edit"
          element={
            <ProtectedRoute>
              <CommunityEditPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/rooms"
          element={
            <ProtectedRoute>
              <RoomListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/rooms/:roomId"
          element={
            <ProtectedRoute>
              <RoomPage />
            </ProtectedRoute>
          }
        />
        <Route path="/community/list" element={<Navigate to="/community" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default App;
