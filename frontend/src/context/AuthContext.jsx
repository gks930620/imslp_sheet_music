import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { authFetch, toApiError } from "../lib/http.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState("loading");

  const loadMe = async () => {
    try {
      const response = await authFetch("/api/users/me", { credentials: "include" });
      if (!response.ok) {
        setUser(null);
        setStatus("guest");
        return null;
      }

      const result = await response.json();
      setUser(result?.data ?? null);
      setStatus("authenticated");
      return result?.data ?? null;
    } catch {
      setUser(null);
      setStatus("guest");
      return null;
    }
  };

  useEffect(() => {
    loadMe();
  }, []);

  // refresh 실패(세션 만료/폐기) 시 게스트로 강등한다. (http.js가 auth:expired 발행)
  useEffect(() => {
    const handleAuthExpired = () => {
      setUser(null);
      setStatus("guest");
    };
    window.addEventListener("auth:expired", handleAuthExpired);
    return () => window.removeEventListener("auth:expired", handleAuthExpired);
  }, []);

  const login = async ({ username, password }) => {
    const response = await fetch("/api/login", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json, text/html",
      },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      throw await toApiError(response);
    }

    return loadMe();
  };

  const logout = async () => {
    // 서버 호출이 실패(네트워크 단절 등)해도 클라이언트 인증 상태는 반드시 해제한다.
    // (기존엔 fetch 예외 시 setUser(null)이 실행되지 않아 로그아웃이 안 되고 unhandled rejection 발생)
    try {
      await fetch("/api/logout", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
      });
    } catch {
      // 무시: 아래 finally에서 클라이언트 상태 정리
    } finally {
      setUser(null);
      setStatus("guest");
    }
  };

  const value = useMemo(
    () => ({
      user,
      status,
      isAuthenticated: status === "authenticated" && !!user,
      refreshUser: loadMe,
      login,
      logout,
    }),
    [user, status],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
