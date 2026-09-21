import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { MyLibraryPage } from "./MyLibraryPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCalls, fakeResponse } from "../../test/apiMock.js";
import { workSummary } from "../../test/fixtures.js";
import { favoritesResponse, downloadsResponse, libraryCounts } from "../../test/fixtures.library.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

/**
 * 내 악보 — 화면정의 09 §1(탭·즐겨찾기 탭·빈 상태·상태별 UI), 기획 05 §2, 인수 조건 8-C 2~8 · 8-F 1~3.
 * 계약: 02 §10-2(목록·counts 2개) · §10-1(해제는 즉시 서버 반영) · §10-4(주소).
 *
 * 주소가 확정값이다: `/piano/library/favorites` · `/piano/library/downloads`(02 §10-4).
 */

const FAVORITES_ROUTE = "/piano/library/favorites";
const LOGIN_INTENT_KEY = "sheetmusic.loginIntent";

function renderLibrary({ route = FAVORITES_ROUTE, auth = "user", routes = [] } = {}) {
  mockFetch([
    { url: /\/api\/me\/library\/favorites/, data: favoritesResponse({ counts: libraryCounts(2, 5) }) },
    { url: /\/api\/me\/library\/downloads/, data: downloadsResponse({ counts: libraryCounts(2, 5) }) },
    { url: /\/api\/me\/favorites\/\d+/, method: "PUT", data: { workId: 21, favorited: true } },
    { url: /\/api\/me\/favorites\/\d+/, method: "DELETE", status: 204, raw: "" },
    ...routes,
  ]);
  return renderWithProviders(<MyLibraryPage />, { route, path: "/:section/library/:tab", auth });
}

function tabLink(name) {
  return screen.queryByRole("link", { name });
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

describe("내 악보 — 제목과 두 탭 (8-C 2, 8-F 3)", () => {
  it("h1 '내 악보' + 탭 2개, 탭마다 자기 주소, 선택 탭은 aria-current", async () => {
    renderLibrary();
    await findText("즐겨찾기");

    expect(screen.getByRole("heading", { level: 1, name: "내 악보" })).toBeInTheDocument();
    const favorites = tabLink(/즐겨찾기/);
    const downloads = tabLink(/받은 악보/);
    expect(favorites).toHaveAttribute("href", "/piano/library/favorites");
    expect(downloads).toHaveAttribute("href", "/piano/library/downloads");
    expect(favorites).toHaveAttribute("aria-current", "page");
    expect(downloads).not.toHaveAttribute("aria-current");
  });

  it("탭에 숫자 2개가 함께 보인다 — 어느 탭에 있든 (09 §6 S3)", async () => {
    renderLibrary();
    await findText("즐겨찾기 (2)");

    expectText("받은 악보 (5)");
  });

  it("탭은 role=tab 이 아니라 링크다 (09 F2 — 밑줄 = 주소가 바뀌는 이동)", async () => {
    renderLibrary();
    await findText("즐겨찾기");

    expect(screen.queryAllByRole("tab")).toHaveLength(0);
    expect(screen.getByRole("navigation", { name: "내 악보" })).toBeInTheDocument();
  });

  it("숫자를 모르는 동안 (0) 을 그리지 않는다 — 괄호째 생략 (09 F6)", async () => {
    let release;
    const gate = new Promise((resolve) => {
      release = resolve;
    });
    renderLibrary({
      routes: [
        {
          url: /\/api\/me\/library\/favorites/,
          handler: async () => {
            await gate;
            return fakeResponse({ success: true, message: "성공", data: favoritesResponse({ counts: libraryCounts(2, 5) }) });
          },
        },
      ],
    });

    await findText("즐겨찾기");
    expectNoText("즐겨찾기 (0)");
    expectNoText("받은 악보 (0)");
    release();
    await findText("즐겨찾기 (2)");
  });

  it("받은 악보 탭 주소로 열면 그 탭이 선택돼 있다", async () => {
    renderLibrary({ route: "/piano/library/downloads" });
    await findText("받은 악보");

    expect(tabLink(/받은 악보/)).toHaveAttribute("aria-current", "page");
    expect(tabLink(/즐겨찾기/)).not.toHaveAttribute("aria-current");
    await waitFor(() => expect(findCalls(/\/api\/me\/library\/downloads/)).toHaveLength(1));
  });

  it("목록은 그 구분으로 부른다 (02 §10-2 · 기획 05 §5-2)", async () => {
    renderLibrary();
    await findText("즐겨찾기 (2)");

    const call = findCalls(/\/api\/me\/library\/favorites/)[0];
    expect(call.params.get("section")).toBe("PIANO");
  });
});

describe("즐겨찾기 탭 — 목록과 해제 (8-C 3·4)", () => {
  it("항목은 다른 목록과 같은 곡 카드이고 누르면 곡 상세로 간다", async () => {
    renderLibrary();
    await findText("월광 소나타");

    const card = screen.getByRole("link", { name: /월광 소나타/ });
    expect(card).toHaveAttribute("href", "/piano/works/21");
    expectText("베토벤");
    expectText("중급");
  });

  it("카드 안에 즐겨찾기 버튼·표시를 두지 않는다 — 해제 버튼은 카드 링크 밖이다 (09 F1)", async () => {
    renderLibrary();
    await findText("월광 소나타");

    const card = screen.getByRole("link", { name: /월광 소나타/ });
    expect(within(card).queryByRole("button")).toBeNull();
  });

  it("'즐겨찾기 해제' 를 누르면 즉시 목록에서 빠지고 서버에도 바로 반영된다 (09 §6 S6)", async () => {
    const user = userEvent.setup();
    renderLibrary();
    await findText("월광 소나타");

    await user.click(screen.getAllByRole("button", { name: "즐겨찾기 해제" })[0]);

    expectNoText("월광 소나타");
    await waitFor(() => expect(findCalls(/\/api\/me\/favorites\/21/)).toHaveLength(1));
    expect(findCalls(/\/api\/me\/favorites\/21/)[0].method).toBe("DELETE");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expectText("즐겨찾기 (1)");
  });

  it("해제하면 '즐겨찾기에서 뺐어요' + 되돌리기, 되돌리면 제자리로 돌아온다 (09 §1-1-2)", async () => {
    const user = userEvent.setup();
    renderLibrary();
    await findText("월광 소나타");

    await user.click(screen.getAllByRole("button", { name: "즐겨찾기 해제" })[0]);
    await findText("즐겨찾기에서 뺐어요");

    await user.click(screen.getByRole("button", { name: "되돌리기" }));

    await findText("월광 소나타");
    await waitFor(() => {
      const puts = findCalls(/\/api\/me\/favorites\/21/).filter((call) => call.method === "PUT");
      expect(puts).toHaveLength(1);
    });
    expectText("즐겨찾기 (2)");
  });

  it("해제가 실패하면 항목이 제자리로 돌아오고 실패 토스트가 뜬다 (기획 05 §7)", async () => {
    const user = userEvent.setup();
    renderLibrary({ routes: [{ url: /\/api\/me\/favorites\/\d+/, method: "DELETE", reject: true }] });
    await findText("월광 소나타");

    await user.click(screen.getAllByRole("button", { name: "즐겨찾기 해제" })[0]);

    await findText("즐겨찾기를 저장하지 못했어요. 다시 시도해 주세요");
    expectText("월광 소나타");
    expectText("즐겨찾기 (2)");
  });

  it("0곡이면 빈 안내와 홈으로 가는 출구가 보인다 — 빈 표가 아니다 (8-C 5)", async () => {
    renderLibrary({
      routes: [
        {
          url: /\/api\/me\/library\/favorites/,
          data: favoritesResponse({ works: [], counts: libraryCounts(0, 0) }),
        },
      ],
    });
    await findText("아직 즐겨찾기한 곡이 없어요");

    expectText("즐겨찾기를 누르면 여기 모여요");
    expect(screen.getByRole("link", { name: "인기곡 보러 가기" })).toHaveAttribute("href", "/piano");
    expectText("즐겨찾기 (0)");
  });

  it("21곡이면 페이지 이동이 보이고 page 쿼리로 부른다 (8-C 6)", async () => {
    renderLibrary({
      routes: [
        {
          url: /\/api\/me\/library\/favorites/,
          data: favoritesResponse({
            works: Array.from({ length: 20 }, (_, i) => workSummary({ id: 100 + i, titleKo: `곡 ${i + 1}` })),
            counts: libraryCounts(21, 0),
            totalElements: 21,
          }),
        },
      ],
    });
    await findText("곡 1");

    expect(screen.getByRole("button", { name: "2" })).toBeInTheDocument();
  });
});

describe("들어가는 길 — 로그인 (8-C 8)", () => {
  it("비로그인은 화면을 그리지 않고 로그인으로 보낸다 (reason=library, 돌아갈 주소는 그 탭)", async () => {
    const { getLocation } = renderLibrary({ auth: "guest", route: "/piano/library/downloads" });

    await waitFor(() => expect(getLocation().pathname).toBe("/login"));
    expect(getLocation().params.get("redirect")).toBe("/piano/library/downloads");
    expect(getLocation().params.get("reason")).toBe("library");
    expectNoText("내 악보");
  });

  it("소셜 로그인 왕복을 위해 돌아갈 주소를 sessionStorage 에도 적어 둔다 (03 §22)", async () => {
    renderLibrary({ auth: "guest" });

    await waitFor(() => expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).not.toBeNull());
    expect(JSON.parse(sessionStorage.getItem(LOGIN_INTENT_KEY))).toMatchObject({
      action: null,
      returnTo: FAVORITES_ROUTE,
    });
  });

  it("인증 확인 중에는 로그인으로 보내지 않는다 — 스피너만 (09 상태표)", async () => {
    const { getLocation } = renderLibrary({ auth: "loading" });

    expect(getLocation().pathname).toBe(FAVORITES_ROUTE);
    expect(screen.getByRole("status")).toBeInTheDocument();
    expectNoText("내 악보");
  });
});

describe("불러오기 실패 — 탭은 살아 있다 (기획 05 §7)", () => {
  it("'연결을 확인해 주세요' + '다시 시도' 로 재요청하고, h1·탭은 그대로다", async () => {
    const user = userEvent.setup();
    renderLibrary({ routes: [{ url: /\/api\/me\/library\/favorites/, reject: true }] });
    await findText("연결을 확인해 주세요");

    expect(screen.getByRole("heading", { level: 1, name: "내 악보" })).toBeInTheDocument();
    expect(tabLink(/즐겨찾기/)).toBeInTheDocument();
    expectNoText("즐겨찾기 (0)");

    mockFetch([{ url: /\/api\/me\/library\/favorites/, data: favoritesResponse({ counts: libraryCounts(2, 5) }) }]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));

    await findText("월광 소나타");
  });
});
