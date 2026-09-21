import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { useDocumentTitle } from "../hooks/useDocumentTitle.js";
import { getErrorMessage } from "../lib/format.js";

/**
 * 09 §3 — 로그인 화면의 "이유 한 줄" 2종. 기존 부제(`서비스에 로그인하세요`) 자리를 **대체**한다.
 * 배너·InlineAlert 로 키우지 않는다(8-G 2 "권유는 로그인 화면 안의 한 줄뿐").
 * 값은 화면 전용 쿼리 `reason`(02 §10-4). 알 수 없는 값·없음은 기존 부제 그대로 — 오류가 아니다.
 */
const LOGIN_REASONS = {
  favorite: { icon: "star_border", text: "즐겨찾기는 로그인하면 쓸 수 있어요" },
  library: { icon: "library_music", text: "내 악보는 로그인하면 볼 수 있어요" },
};

export function LoginPage() {
  useDocumentTitle("로그인 — 쉬운악보"); // 00 §4-1 — 구분 밖 화면이라 구분 이름이 붙지 않는다
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [form, setForm] = useState({ username: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const username = searchParams.get("username");
    if (username) {
      setForm((prev) => ({ ...prev, username }));
    }
  }, [searchParams]);

  // 00_공통 §8: /login?redirect= 로 돌아갈 주소를 받는다 (관리 화면 딥링크 복귀)
  const target = searchParams.get("redirect") || location.state?.from || "/";
  const reason = LOGIN_REASONS[searchParams.get("reason")] ?? null;

  // 로그인 성공(또는 이미 인증됨) 시 단일 지점에서 목적지로 이동.
  // 로그인 후 login()이 인증 상태를 갱신 → 이 effect가 이전 페이지(target)로 이동시킨다.
  // (기존엔 여기서 "/"로 보내면서 onSubmit의 navigate(target)를 덮어써 딥링크 복귀가 깨졌음)
  useEffect(() => {
    if (isAuthenticated) {
      navigate(target, { replace: true });
    }
  }, [isAuthenticated, navigate, target]);

  const onSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(form);
    } catch (e) {
      setError(getErrorMessage(e, "로그인에 실패했습니다."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="logo-section">
        <div className="logo-icon">
          <span className="material-icons">toys</span>
        </div>
        <h1 className="logo-title">로그인</h1>
        {reason ? (
          <p className="logo-subtitle login-reason">
            <span className="material-icons" aria-hidden="true">
              {reason.icon}
            </span>
            {reason.text}
          </p>
        ) : (
          <p className="logo-subtitle">서비스에 로그인하세요</p>
        )}
      </div>

      <form onSubmit={onSubmit}>
        <div className="input-group">
          <label htmlFor="loginUsername">아이디</label>
          <div className="input-wrapper">
            <span className="material-icons">person</span>
            <input
              id="loginUsername"
              required
              className="input-field"
              type="text"
              placeholder="아이디를 입력하세요"
              value={form.username}
              onChange={(e) => setForm((prev) => ({ ...prev, username: e.target.value }))}
            />
          </div>
        </div>

        <div className="input-group">
          <label htmlFor="loginPassword">비밀번호</label>
          <div className="input-wrapper">
            <span className="material-icons">lock</span>
            <input
              id="loginPassword"
              required
              className="input-field"
              type="password"
              placeholder="비밀번호를 입력하세요"
              value={form.password}
              onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))}
            />
          </div>
        </div>

        <div className={`error-message ${error ? "show" : ""}`}>{error}</div>

        <button type="submit" className="submit-btn" disabled={loading}>
          {loading ? "로그인 중..." : "로그인"}
        </button>
      </form>

      <div className="divider">
        <span>또는</span>
      </div>

      <div className="social-login">
        <a href="/custom-oauth2/login/web/kakao" className="social-btn kakao">
          <svg className="icon" viewBox="0 0 24 24">
            <path fill="#000000" d="M12 3c5.799 0 10.5 3.664 10.5 8.185 0 4.52-4.701 8.184-10.5 8.184a13.5 13.5 0 0 1-1.727-.11l-4.408 2.883c-.501.265-.678.236-.472-.413l.892-3.678c-2.88-1.46-4.785-3.99-4.785-6.866C1.5 6.665 6.201 3 12 3z" />
          </svg>
          <span>카카오로 시작하기</span>
        </a>

        <a href="/custom-oauth2/login/web/google" className="social-btn google">
          <svg className="icon" viewBox="0 0 24 24">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
          </svg>
          <span>구글로 시작하기</span>
        </a>
      </div>

      <div className="home-link">
        <span>계정이 없으신가요? </span>
        <Link to="/signup">회원가입</Link>
      </div>
      <div className="home-link">
        <Link to={target}>이전 페이지로 돌아가기</Link>
      </div>
    </div>
  );
}
