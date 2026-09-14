import { useEffect, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { useDocumentTitle } from "../hooks/useDocumentTitle.js";
import { InlineAlert } from "./common/InlineAlert.jsx";

/** 00_공통 §4 세션 만료(관리) — 안내를 보여 주는 시간 */
const EXPIRED_NOTICE_MS = 2000;

function isAdmin(user) {
  return Boolean(user?.roles?.includes("ADMIN"));
}

/**
 * 05_관리자 공통 — 관리 화면 진입 가드.
 * 비로그인 → /login?redirect={원래 경로} / USER → AccessDenied(같은 주소) / ADMIN → 통과
 */
export function AdminRoute({ children }) {
  const { user, status, isAuthenticated } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [expiredFrom, setExpiredFrom] = useState(null);
  // 00 §4-1 — 관리 화면 전부 한 제목. 구분 밖이라 구분 이름이 붙지 않는다(08 §5). 가드가 공통이라 새 관리 화면에서 빠지지 않는다
  useDocumentTitle("관리 — 쉬운악보");

  const currentPath = location.pathname + location.search;

  // lib/http.js 가 refresh 실패 시 발행하는 신호. 관리 화면마다 붙이면 새 화면에서 빠지므로 공통 가드가 받는다.
  useEffect(() => {
    const handleExpired = () => setExpiredFrom(currentPath);
    window.addEventListener("auth:expired", handleExpired);
    return () => window.removeEventListener("auth:expired", handleExpired);
  }, [currentPath]);

  useEffect(() => {
    if (!expiredFrom) return undefined;
    const timer = setTimeout(() => {
      navigate(`/login?redirect=${encodeURIComponent(expiredFrom)}`, { replace: true });
    }, EXPIRED_NOTICE_MS);
    return () => clearTimeout(timer);
  }, [expiredFrom, navigate]);

  // AuthContext 강등이 같은 순간에 일어난다. 아래 !isAuthenticated 분기보다 먼저 판단해야
  // 안내 없이 로그인 화면으로 튕기지 않는다.
  if (expiredFrom) {
    return (
      <section className="panel admin-session-expired">
        <InlineAlert variant="danger">
          <p>로그인이 풀렸어요. 다시 로그인해 주세요</p>
          <p>작성 중이던 내용은 저장되지 않았어요</p>
        </InlineAlert>
      </section>
    );
  }

  if (status === "loading") {
    return (
      <section className="panel admin-route-loading">
        <div className="spinner" role="status" aria-label="인증 상태 확인 중" />
      </section>
    );
  }

  if (!isAuthenticated) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate replace to={`/login?redirect=${redirect}`} />;
  }

  if (!isAdmin(user)) {
    return (
      <section className="empty-state">
        <span className="material-icons">lock</span>
        <h3>관리자만 들어갈 수 있어요</h3>
        <div className="empty-state-actions">
          <Link className="btn btn-primary" to="/">
            홈으로
          </Link>
        </div>
      </section>
    );
  }

  return children;
}
