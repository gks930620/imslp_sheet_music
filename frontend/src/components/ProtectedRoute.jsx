import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";

export function ProtectedRoute({ children }) {
  const { status, isAuthenticated } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return <section className="panel">인증 상태 확인 중...</section>;
  }

  if (!isAuthenticated) {
    return <Navigate replace state={{ from: location.pathname + location.search }} to="/login" />;
  }

  return children;
}
