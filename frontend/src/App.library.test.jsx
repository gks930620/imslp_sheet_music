import { waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import App from "./App.jsx";
import { renderWithProviders, readLocation } from "./test/renderWithProviders.jsx";
import { mockFetch } from "./test/apiMock.js";
import { featuredComposers, popularWorks } from "./test/fixtures.js";
import { favoritesResponse, downloadsResponse, libraryCounts } from "./test/fixtures.library.js";
import { findText } from "./test/text.js";

/**
 * 내 악보의 주소 — 02 §10-4(확정), 00 §8 라우트 표, 기획 05 §2-1 3 · §5-1·5-7,
 * 인수 조건 8-F 1·3·5 · 8-C 8.
 *
 * 확정값: `/piano/library` → `/piano/library/favorites`(replace) · `/piano/library/downloads`.
 * 준비 중 구분 아래(`/violin/library`)와 없는 탭 이름은 **찾을 수 없는 페이지**다.
 */

function renderApp(route, auth = "user") {
  mockFetch([
    { url: /\/api\/works\/popular/, data: popularWorks(3) },
    { url: /\/api\/composers\/featured/, data: featuredComposers(3) },
    { url: /\/api\/works\/recent/, data: [] },
    { url: /\/api\/me\/library\/favorites/, data: favoritesResponse({ counts: libraryCounts(2, 1) }) },
    { url: /\/api\/me\/library\/downloads/, data: downloadsResponse({ counts: libraryCounts(2, 1) }) },
    { url: "/api/admin/crawl/jobs/active", data: null },
  ]);
  return renderWithProviders(<App />, { route, auth });
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

describe("내 악보 주소 (8-F 1·3)", () => {
  it("/piano/library 는 즐겨찾기 탭으로 replace 된다 — 한 화면에 주소가 둘이 되지 않게", async () => {
    renderApp("/piano/library");

    await waitFor(() => expect(readLocation().pathname).toBe("/piano/library/favorites"));
    await findText("내 악보");
  });

  it("탭 주소를 그대로 열면 그 탭이 열린다", async () => {
    renderApp("/piano/library/downloads");

    await findText("받은 악보");
    expect(readLocation().pathname).toBe("/piano/library/downloads");
  });

  it("준비 중 구분 아래에는 내 악보가 없다 — 찾을 수 없는 페이지 (8-F 5)", async () => {
    renderApp("/violin/library");

    await findText("찾을 수 없는 페이지예요");
  });

  it("없는 탭 이름도 찾을 수 없는 페이지다", async () => {
    renderApp("/piano/library/xyz");

    await findText("찾을 수 없는 페이지예요");
  });

  it("비로그인으로 탭 주소에 들어가면 로그인 화면으로 (이유·복귀 주소를 들고) (8-C 8)", async () => {
    renderApp("/piano/library/favorites", "guest");

    await waitFor(() => expect(readLocation().pathname).toBe("/login"));
    expect(readLocation().params.get("redirect")).toBe("/piano/library/favorites");
    expect(readLocation().params.get("reason")).toBe("library");
  });
});
