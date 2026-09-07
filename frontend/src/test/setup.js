import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// AuthContext 는 모든 테스트에서 목으로 대체한다 (src/test/authMock.js).
// 실제 AuthProvider 는 마운트 시 /api/users/me 를 호출하므로 화면 테스트에 섞이면 fetch 목이 오염된다.
vi.mock("../context/AuthContext.jsx", async () => {
  const mock = await import("./authMock.js");
  return { AuthProvider: mock.MockAuthProvider, useAuth: mock.useAuth };
});

// jsdom 이 구현하지 않는 브라우저 API
window.scrollTo = vi.fn();
Element.prototype.scrollIntoView = vi.fn();
if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
