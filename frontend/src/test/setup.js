import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";
import { dismissToast } from "../components/common/Toast.jsx";

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
  // Toast 는 화면 언마운트 뒤에도 남도록 document.body 에 직접 붙는다(Toast.jsx) — RTL cleanup 이 걷어가지 못한다.
  // 3초 타이머가 다음 테스트까지 살아 있으면 앞 테스트의 문구가 남아 role="status" 조회가 둘이 된다.
  dismissToast();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
