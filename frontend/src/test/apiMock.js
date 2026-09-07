import { vi } from "vitest";

/**
 * fetch 목. 응답 본문은 02_API_명세서 의 JSON 예시(fixtures.js)를 그대로 쓴다.
 *
 * mockFetch([
 *   { url: "/api/works/popular?limit=10", data: [...] },                 // 200 + ApiResponse 래퍼
 *   { url: /\/api\/works\/\d+$/, status: 404, error: "NOT_FOUND", message: "곡을 찾을 수 없어요" },
 *   { url: "/api/admin/crawl/check", method: "POST", data: {...} },
 *   { url: "/api/x", reject: true },                                    // 네트워크 오류(TypeError)
 *   { url: "/api/y", handler: (url, init) => fakeResponse(...) },
 * ])
 * url 문자열은 "쿼리 포함 정확 일치" 또는 "경로만 일치(쿼리 무시)" 둘 다 허용. 정규식이면 test().
 * 나중에 등록된 규칙이 우선한다(같은 URL 을 덮어쓸 때 편하게).
 */
export function mockFetch(routes = []) {
  const table = [...routes].reverse();
  const fn = vi.fn(async (input, init = {}) => {
    const url = typeof input === "string" ? input : input.url;
    const method = (init.method ?? "GET").toUpperCase();
    const rule = table.find((r) => matches(r, url, method));
    if (!rule) {
      return fakeResponse({ success: false, message: `mock 없음: ${method} ${url}`, errorCode: "NOT_FOUND" }, 404);
    }
    if (rule.delay) await new Promise((resolve) => setTimeout(resolve, rule.delay));
    if (rule.reject) throw new TypeError("Failed to fetch");
    if (rule.handler) return rule.handler(url, init);
    if (rule.status && rule.status >= 400) {
      return fakeResponse(
        {
          success: false,
          message: rule.message ?? "오류",
          errorCode: rule.error ?? "UNKNOWN_ERROR",
          ...(rule.errors ? { errors: rule.errors } : {}),
        },
        rule.status,
      );
    }
    if (rule.raw !== undefined) return fakeResponse(rule.raw, rule.status ?? 200);
    return fakeResponse({ success: true, message: "성공", data: rule.data ?? null }, rule.status ?? 200);
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}

function matches(rule, url, method) {
  if (rule.method && rule.method.toUpperCase() !== method) return false;
  if (rule.url instanceof RegExp) return rule.url.test(url);
  if (typeof rule.url === "function") return rule.url(url);
  const [path] = url.split("?");
  return rule.url === url || rule.url === path;
}

export function fakeResponse(body, status = 200, headers = {}) {
  const text = typeof body === "string" ? body : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": "application/json", ...headers }),
    json: async () => JSON.parse(text),
    text: async () => text,
    clone() {
      return fakeResponse(body, status, headers);
    },
  };
}

/** fetch 호출 목록을 { url, path, params, method, body } 로 정리 */
export function fetchCalls(fn = globalThis.fetch) {
  return fn.mock.calls.map(([input, init = {}]) => {
    const url = typeof input === "string" ? input : input.url;
    const [path, query = ""] = url.split("?");
    let body = init.body;
    if (typeof body === "string") {
      try {
        body = JSON.parse(body);
      } catch {
        /* 문자열 그대로 */
      }
    }
    return { url, path, params: new URLSearchParams(query), method: (init.method ?? "GET").toUpperCase(), body, init };
  });
}

function hit(call, pattern) {
  return pattern instanceof RegExp ? pattern.test(call.url) : call.url === pattern || call.path === pattern;
}

export function findCall(pattern, fn = globalThis.fetch) {
  return fetchCalls(fn).find((c) => hit(c, pattern));
}

export function findCalls(pattern, fn = globalThis.fetch) {
  return fetchCalls(fn).filter((c) => hit(c, pattern));
}
