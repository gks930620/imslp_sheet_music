import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { MyLibraryPage } from "./MyLibraryPage.jsx";
import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { workDetail } from "../../test/fixtures.js";
import { favoritesResponse, libraryCounts } from "../../test/fixtures.library.js";
import { findText } from "../../test/text.js";

/**
 * 즐겨찾기 토스트가 **어떤 아이콘으로** 뜨는가 — 00 §3-11, 03_기술결정 §29.
 *
 * `Toast.variants.test.jsx` 가 컴포넌트의 계약을 잠근다면, 여기는 **호출부가 그 계약을 옳게 쓰는지** 본다.
 * 기본값이 성공(= 관리 화면 10여 곳을 안 고치기 위한 선택)이라, **실패 문구에 체크표가 붙는 사고**는
 * 컴포넌트가 아니라 호출부에서 난다. 그래서 두 화면의 실패 경로를 여기서 따로 잡는다.
 */

const FAVORITE_FAILED = "즐겨찾기를 저장하지 못했어요. 다시 시도해 주세요";

function icon() {
  return document.querySelector(".toast-icon");
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

describe("곡 상세 — 즐겨찾기 실패 토스트 (00 §3-11 ②)", () => {
  it("실패는 error 아이콘이다 — 체크표가 붙으면 화면이 거짓말을 한다", async () => {
    const user = userEvent.setup();
    mockFetch([
      { url: /\/api\/editions\/\d+\/download/, method: "HEAD", raw: "" },
      { url: /\/api\/works\/21(\?|$)/, data: { ...workDetail(), section: "PIANO", favorited: false } },
      { url: /\/api\/me\/favorites\/\d+/, method: "PUT", reject: true },
    ]);
    renderWithProviders(<WorkDetailPage />, { route: "/piano/works/21", path: "/:section/works/:id", auth: "user" });
    await findText("월광 소나타");

    await user.click(screen.getByRole("button", { name: /즐겨찾기/ }));

    await findText(FAVORITE_FAILED);
    expect(icon()).toHaveTextContent("error");
  });
});

describe("내 악보 — 즐겨찾기 해제 토스트 (00 §3-11 ②③)", () => {
  function renderLibrary(routes = []) {
    mockFetch([
      { url: /\/api\/me\/library\/favorites/, data: favoritesResponse({ counts: libraryCounts(2, 0) }) },
      { url: /\/api\/me\/favorites\/\d+/, method: "DELETE", status: 204, raw: "" },
      ...routes,
    ]);
    return renderWithProviders(<MyLibraryPage />, {
      route: "/piano/library/favorites",
      path: "/:section/library/:tab",
      auth: "user",
    });
  }

  it("해제 성공은 되돌리기 변형이라 아이콘이 없다", async () => {
    const user = userEvent.setup();
    renderLibrary();
    await findText("월광 소나타");

    await user.click(screen.getAllByRole("button", { name: "즐겨찾기 해제" })[0]);

    await findText("즐겨찾기에서 뺐어요");
    expect(icon()).toBeNull();
    expect(screen.getByRole("button", { name: "되돌리기" })).toBeInTheDocument();
  });

  it("해제 실패는 error 아이콘이다", async () => {
    const user = userEvent.setup();
    renderLibrary([{ url: /\/api\/me\/favorites\/\d+/, method: "DELETE", reject: true }]);
    await findText("월광 소나타");

    await user.click(screen.getAllByRole("button", { name: "즐겨찾기 해제" })[0]);

    await findText(FAVORITE_FAILED);
    expect(icon()).toHaveTextContent("error");
  });
});
