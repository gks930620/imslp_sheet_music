import { Navigate, Outlet, Route, Routes } from "react-router-dom";
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
import { AdminDashboardPage } from "./pages/admin/AdminDashboardPage.jsx";
import { CrawlAdminPage } from "./pages/admin/CrawlAdminPage.jsx";
import { CrawlProgressPage } from "./pages/admin/CrawlProgressPage.jsx";
import { ComposerAdminListPage } from "./pages/admin/ComposerAdminListPage.jsx";
import { ComposerFormPage } from "./pages/admin/ComposerFormPage.jsx";
import { WorkAdminListPage } from "./pages/admin/WorkAdminListPage.jsx";
import { WorkFormPage } from "./pages/admin/WorkFormPage.jsx";
import { CopyrightPendingPage } from "./pages/admin/CopyrightPendingPage.jsx";

// 라우트 표: docs/화면정의/00_공통_레이아웃_토큰.md §8
// 관리 경로는 /admin 중첩 라우트 한 곳에서 AdminRoute 로 감싼다(새 관리 화면을 추가해도 가드가 빠지지 않게).
// 등록(/new)은 상세(/:id)보다 먼저 등록한다.
function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/search" element={<SearchResultPage />} />
        <Route path="/works/:id" element={<WorkDetailPage />} />
        <Route path="/composers" element={<ComposerListPage />} />
        <Route path="/composers/:id" element={<ComposerDetailPage />} />

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
