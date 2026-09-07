import { vi } from "vitest";

/**
 * 기존 `useAuth` 계약 `{ user, status, isAuthenticated, login, logout, refreshUser }` 를 그대로 흉내 낸다.
 * setup.js 에서 vi.mock 으로 AuthContext.jsx 를 이 모듈로 바꿔 끼운다.
 * 테스트는 renderWithProviders({ auth }) 로 상태를 정하고, 직접 setAuth 를 호출할 수도 있다.
 */

function build(overrides) {
  return {
    user: null,
    status: "guest",
    isAuthenticated: false,
    login: vi.fn().mockResolvedValue(null),
    logout: vi.fn().mockResolvedValue(undefined),
    refreshUser: vi.fn().mockResolvedValue(null),
    ...overrides,
  };
}

export const guestAuth = () => build({ user: null, status: "guest", isAuthenticated: false });
export const loadingAuth = () => build({ user: null, status: "loading", isAuthenticated: false });
export const userAuth = (user = {}) =>
  build({
    user: { id: 2, username: "user1", nickname: "일반사용자", roles: ["USER"], ...user },
    status: "authenticated",
    isAuthenticated: true,
  });
export const adminAuth = (user = {}) =>
  build({
    user: { id: 3, username: "gks930620", nickname: "관리자", roles: ["USER", "ADMIN"], ...user },
    status: "authenticated",
    isAuthenticated: true,
  });

const presets = { guest: guestAuth, loading: loadingAuth, user: userAuth, admin: adminAuth };

let current = guestAuth();

export function setAuth(value) {
  current = typeof value === "string" ? presets[value]() : { ...build({}), ...value };
  return current;
}

export function getAuth() {
  return current;
}

export function useAuth() {
  return current;
}

export function MockAuthProvider({ children }) {
  return children;
}
