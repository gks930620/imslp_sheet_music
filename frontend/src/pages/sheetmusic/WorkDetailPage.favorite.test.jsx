import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, fakeResponse, findCalls } from "../../test/apiMock.js";
import { dismissToast } from "../../components/common/Toast.jsx";
import { workDetail, edition } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 저장 키는 계약이다(03_기술결정 §22·§23). 여기서는 아직 없는 모듈을 import 하지 않고 **바깥에서** 그 값을
// 그대로 확인한다 — 그래야 이 파일의 실패가 "모듈이 없다" 가 아니라 "화면이 아직 그렇게 동작하지 않는다" 가 된다.
// (키 이름 자체는 `loginIntent.test.js` · `recentWorks.test.js` 가 상수로 잠근다.)
const LOGIN_INTENT_KEY = "sheetmusic.loginIntent";
const RECENT_WORKS_KEY = "sheetmusic.recentWorks";

function readRecentWorkIds(section = "PIANO") {
  try {
    return JSON.parse(localStorage.getItem(RECENT_WORKS_KEY) ?? "{}")[section] ?? [];
  } catch {
    return [];
  }
}

/**
 * 곡 상세의 즐겨찾기 — 화면정의 03 §3-6(+ 09 §3), 기획 05 §1, 인수 조건 8-A 1~8 · 8-B 1~7 · 8-E 9.
 * 계약: 02 §3-3 `favorited` · §10-1 PUT/DELETE · 03_기술결정 §22(복귀 완성) · §23(최근 본 곡 저장).
 *
 * 이 파일이 지키는 어려운 것 셋:
 *   ⑴ 돌아왔을 때 이미 켜져 있다        (8-B 3)
 *   ⑵ 새로고침에 두 번 실행되지 않는다   (8-B 4)
 *   ⑶ 돌아가기·뒤로 가기로는 실행되지 않는다 (8-B 6)
 */

const WORK_ID = 21;
const ROUTE = `/piano/works/${WORK_ID}`;

function detail(overrides = {}) {
  return { ...workDetail(overrides), section: "PIANO", favorited: false, ...overrides };
}

/**
 * 쿼리가 붙어도 걸리는 목 — 곡 상세·다운로드 HEAD·즐겨찾기 PUT/DELETE.
 *
 * `fetchMock` 을 함께 돌려주는 이유: `mockFetch` 는 부를 때마다 **새 목**을 전역에 꽂고
 * `findCalls()` 는 기본으로 **가장 최근 목**을 읽는다. 한 테스트가 두 번 마운트할 때(새로고침)
 * "몇 번째 마운트가 무엇을 보냈나" 를 가르려면 그 마운트의 목을 손에 들고 있어야 한다.
 */
function renderDetail({ data = detail(), auth = "user", favoriteRule = null } = {}) {
  const fetchMock = mockFetch([
    { url: /\/api\/editions\/\d+\/download/, method: "HEAD", raw: "" },
    { url: /\/api\/works\/21(\?|$)/, data },
    { url: /\/api\/me\/favorites\/\d+/, method: "PUT", data: { workId: WORK_ID, favorited: true } },
    { url: /\/api\/me\/favorites\/\d+/, method: "DELETE", status: 204, raw: "" },
    ...(favoriteRule ? [favoriteRule] : []),
  ]);
  return { ...renderWithProviders(<WorkDetailPage />, { route: ROUTE, path: "/:section/works/:id", auth }), fetchMock };
}

function favoriteButton() {
  return screen.queryByRole("button", { name: /즐겨찾기/ });
}

/** @param fetchMock 그 마운트의 목. 생략하면 지금 꽂혀 있는 목(= 마지막 마운트의 것) */
function favoriteCalls(method, fetchMock) {
  return findCalls(/\/api\/me\/favorites\//, fetchMock ?? globalThis.fetch).filter((call) => call.method === method);
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

describe("즐겨찾기 버튼 — 2상태 (8-A 1·2·3)", () => {
  it("꺼짐은 '즐겨찾기' + aria-pressed=false, 누르면 즉시 '즐겨찾기됨' + PUT", async () => {
    const user = userEvent.setup();
    renderDetail();
    await findText("월광 소나타");

    const button = favoriteButton();
    expect(button).toHaveTextContent("즐겨찾기");
    expect(button).toHaveAttribute("aria-pressed", "false");

    await user.click(button);

    expect(favoriteButton()).toHaveTextContent("즐겨찾기됨");
    expect(favoriteButton()).toHaveAttribute("aria-pressed", "true");
    await waitFor(() => expect(favoriteCalls("PUT")).toHaveLength(1));
    expect(favoriteCalls("PUT")[0].path).toBe(`/api/me/favorites/${WORK_ID}`);
    // 확인 창·화면 이동 없음
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("켜짐 상태로 열리면 처음부터 '즐겨찾기됨' — 곡 상세 응답의 favorited 를 쓴다 (09 §6 S5)", async () => {
    renderDetail({ data: detail({ favorited: true }) });
    await findText("월광 소나타");

    expect(favoriteButton()).toHaveTextContent("즐겨찾기됨");
    expect(favoriteButton()).toHaveAttribute("aria-pressed", "true");
    expect(favoriteCalls("PUT")).toHaveLength(0);
  });

  it("다시 누르면 풀린다 — DELETE, 확인 창 없음 (8-A 2)", async () => {
    const user = userEvent.setup();
    renderDetail({ data: detail({ favorited: true }) });
    await findText("월광 소나타");

    await user.click(favoriteButton());

    expect(favoriteButton()).toHaveTextContent("즐겨찾기");
    expect(favoriteButton()).toHaveAttribute("aria-pressed", "false");
    await waitFor(() => expect(favoriteCalls("DELETE")).toHaveLength(1));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("켜고 끌 때 성공 토스트는 없다 — 버튼이 바뀐 것이 피드백 (03 §3-6-1)", async () => {
    const user = userEvent.setup();
    renderDetail();
    await findText("월광 소나타");

    await user.click(favoriteButton());

    await waitFor(() => expect(favoriteCalls("PUT")).toHaveLength(1));
    expectNoText("즐겨찾기에 넣었어요");
  });

  it("저장 실패면 누르기 전 상태로 돌아가고 실패 토스트가 뜬다. 화면 이동 없음 (8-A 7)", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail({
      favoriteRule: { url: /\/api\/me\/favorites\//, method: "PUT", reject: true },
    });
    await findText("월광 소나타");

    await user.click(favoriteButton());

    await findText("즐겨찾기를 저장하지 못했어요. 다시 시도해 주세요");
    expect(favoriteButton()).toHaveTextContent("즐겨찾기");
    expect(favoriteButton()).toHaveAttribute("aria-pressed", "false");
    expect(getLocation().pathname).toBe(ROUTE);
  });

  it("준비 중 곡에도 버튼이 있다 — 다운로드 버튼이 없어도 (8-A 4)", async () => {
    renderDetail({
      data: detail({ status: "PREPARING", recommendedEdition: null, imslpCandidateEdition: edition({ hasFile: false }) }),
    });
    await findText("악보를 준비하고 있어요");

    expect(favoriteButton()).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /PDF 받기/ })).not.toBeInTheDocument();
  });

  it("'N명이 즐겨찾기' 같은 숫자는 없다 (8-G 6)", async () => {
    renderDetail();
    await findText("월광 소나타");

    expectNoText("명이 즐겨찾기");
  });
});

describe("아이콘 교체 — 같은 화면에서 별이 둘이 되지 않게 (8-A 8, 03 §3-6-4)", () => {
  it("악장 안내 줄은 menu_book 이고 bookmark 는 어디에도 없다", async () => {
    renderDetail();
    await findText("악장 안내");

    expectText("menu_book");
    expectNoText("bookmark");
  });

  it("추천 판본 라벨은 thumb_up 이다", async () => {
    renderDetail();
    await findText("추천 판본");

    expectText("thumb_up");
  });
});

describe("비로그인 — 로그인 화면으로 (8-B 1·2)", () => {
  it("버튼은 보인다(꺼짐). 누르면 저장하지 않고 /login?redirect=&reason=favorite 로 간다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail({ auth: "guest" });
    await findText("월광 소나타");

    expect(favoriteButton()).toHaveTextContent("즐겨찾기");
    await user.click(favoriteButton());

    await waitFor(() => expect(getLocation().pathname).toBe("/login"));
    expect(getLocation().params.get("redirect")).toBe(ROUTE);
    expect(getLocation().params.get("reason")).toBe("favorite");
    expect(favoriteCalls("PUT")).toHaveLength(0);
  });

  it("떠나기 전에 '이 곡을 즐겨찾기하려 했다' 를 sessionStorage 에 적어 둔다 (03 §22)", async () => {
    const user = userEvent.setup();
    renderDetail({ auth: "guest" });
    await findText("월광 소나타");

    await user.click(favoriteButton());

    await waitFor(() => expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).not.toBeNull());
    const intent = JSON.parse(sessionStorage.getItem(LOGIN_INTENT_KEY));
    expect(intent).toMatchObject({ action: "FAVORITE", workId: WORK_ID, returnTo: ROUTE });
  });

  it("인증 확인 중(헤더 '…')에 눌러도 로그인 화면으로 튕기지 않고 아무 요청도 보내지 않는다 (8-B 7)", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail({ auth: "loading" });
    await findText("월광 소나타");

    await user.click(favoriteButton());

    expect(getLocation().pathname).toBe(ROUTE);
    expect(favoriteCalls("PUT")).toHaveLength(0);
    expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).toBeNull();
  });

  it("세션이 조용히 풀려 저장이 401 이면 같은 길로 간다 — 로그인 화면 + 의도 보관", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail({
      favoriteRule: { url: /\/api\/me\/favorites\//, method: "PUT", status: 401, error: "NOT_AUTHENTICATED" },
    });
    await findText("월광 소나타");

    await user.click(favoriteButton());

    await waitFor(() => expect(getLocation().pathname).toBe("/login"));
    expect(getLocation().params.get("reason")).toBe("favorite");
    expect(JSON.parse(sessionStorage.getItem(LOGIN_INTENT_KEY))).toMatchObject({ action: "FAVORITE", workId: WORK_ID });
  });
});

describe("돌아와서 완성 — 한 번만 (8-B 3·4·6)", () => {
  function givenIntent(overrides = {}) {
    sessionStorage.setItem(
      LOGIN_INTENT_KEY,
      JSON.stringify({ action: "FAVORITE", workId: WORK_ID, returnTo: ROUTE, at: Date.now(), ...overrides }),
    );
  }

  it("로그인해 돌아오면 버튼이 이미 켜져 있고 토스트가 한 번 뜬다 (8-B 3)", async () => {
    givenIntent();
    renderDetail();
    await findText("월광 소나타");

    await waitFor(() => expect(favoriteCalls("PUT")).toHaveLength(1));
    await waitFor(() => expect(favoriteButton()).toHaveAttribute("aria-pressed", "true"));
    expect(favoriteButton()).toHaveTextContent("즐겨찾기됨");
    await findText("즐겨찾기에 넣었어요");
  });

  it("의도는 요청 전에 지워진다 — 새로고침해도 두 번 실행되지 않는다 (8-B 4)", async () => {
    givenIntent();
    // 요청이 **나가는 그 순간** 저장소가 이미 비어 있어야 한다. 응답 뒤에 지우면, 응답이 오기 전에
    // 새로고침한 사람에게 의도가 남아 두 번 실행된다(03 §22-2 "요청을 보내기 전에 지운다")
    let intentWhenRequested = "요청이 없었다";
    const first = renderDetail({
      favoriteRule: {
        url: /\/api\/me\/favorites\//,
        method: "PUT",
        handler: () => {
          intentWhenRequested = sessionStorage.getItem(LOGIN_INTENT_KEY);
          return fakeResponse({ success: true, message: "성공", data: { workId: WORK_ID, favorited: true } });
        },
      },
    });
    await findText("월광 소나타");

    await waitFor(() => expect(favoriteCalls("PUT", first.fetchMock)).toHaveLength(1));
    expect(intentWhenRequested).toBeNull();
    expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).toBeNull();
    await findText("즐겨찾기에 넣었어요");

    // 새로고침 = 문서를 통째로 다시 그린다. Toast 는 body 에 직접 붙어 언마운트로는 걷히지 않으므로
    // 여기서 함께 내린다 — 그래야 아래의 "토스트가 다시 뜨지 않는다" 가 앞 토스트에 속지 않는다
    first.unmount();
    dismissToast();

    // 같은 주소로 다시 그린다. 이미 켜진 상태로 응답이 온다
    const second = renderDetail({ data: detail({ favorited: true }) });
    await findText("월광 소나타");
    await waitFor(() => expect(favoriteButton()).toHaveAttribute("aria-pressed", "true"));

    // 두 번째 마운트는 아무것도 다시 실행하지 않는다(의도는 첫 번째가 이미 가져갔다)
    expect(favoriteCalls("PUT", second.fetchMock)).toHaveLength(0);
    // 완성은 첫 번째에서 정확히 한 번이었다 — 두 마운트를 합쳐도 1회다
    expect(favoriteCalls("PUT", first.fetchMock)).toHaveLength(1);
    expectNoText("즐겨찾기에 넣었어요");
  });

  it("비로그인으로 돌아오면(돌아가기·뒤로 가기) 실행되지 않고 안내도 없다 (8-B 6)", async () => {
    givenIntent();
    renderDetail({ auth: "guest" });
    await findText("월광 소나타");

    await waitFor(() => expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).toBeNull());
    expect(favoriteCalls("PUT")).toHaveLength(0);
    expect(favoriteButton()).toHaveAttribute("aria-pressed", "false");
    expectNoText("즐겨찾기에 넣었어요");
  });

  it("다른 곡의 의도는 이 곡에서 실행되지 않는다", async () => {
    givenIntent({ workId: 999, returnTo: "/piano/works/999" });
    renderDetail();
    await findText("월광 소나타");

    await waitFor(() => expect(favoriteButton()).toHaveAttribute("aria-pressed", "false"));
    expect(favoriteCalls("PUT")).toHaveLength(0);
    expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).not.toBeNull();
  });

  it("10분이 지난 의도는 실행되지 않는다 (03 §22-2)", async () => {
    givenIntent({ at: Date.now() - 11 * 60 * 1000 });
    renderDetail();
    await findText("월광 소나타");

    await waitFor(() => expect(favoriteButton()).toHaveAttribute("aria-pressed", "false"));
    expect(favoriteCalls("PUT")).toHaveLength(0);
  });

  it("완성이 실패하면 버튼은 꺼진 채 실패 토스트 — 사용자가 다시 누르면 된다 (03 §3-6-3)", async () => {
    givenIntent();
    renderDetail({ favoriteRule: { url: /\/api\/me\/favorites\//, method: "PUT", reject: true } });
    await findText("월광 소나타");

    await findText("즐겨찾기를 저장하지 못했어요. 다시 시도해 주세요");
    expect(favoriteButton()).toHaveAttribute("aria-pressed", "false");
    expectNoText("즐겨찾기에 넣었어요");
  });
});

describe("최근 본 곡 기록 — 정상으로 열렸을 때만 (8-E 9, 03 §23)", () => {
  it("곡 정보가 그려지면 이 브라우저의 최근 본 곡에 맨 앞으로 들어간다. 화면 변화는 없다", async () => {
    renderDetail();
    await findText("월광 소나타");

    await waitFor(() => expect(readRecentWorkIds("PIANO")).toEqual([WORK_ID]));
    expectNoText("최근 본 곡");
  });

  it("404(없는 곡·숨김 곡)는 기록하지 않는다", async () => {
    mockFetch([{ url: /\/api\/works\/21(\?|$)/, status: 404, error: "NOT_FOUND", message: "곡을 찾을 수 없어요" }]);
    renderWithProviders(<WorkDetailPage />, { route: ROUTE, path: "/:section/works/:id", auth: "user" });

    await findText("찾을 수 없는 페이지예요");
    expect(localStorage.getItem(RECENT_WORKS_KEY)).toBeNull();
  });

  it("불러오기 실패도 기록하지 않는다", async () => {
    mockFetch([{ url: /\/api\/works\/21(\?|$)/, reject: true }]);
    renderWithProviders(<WorkDetailPage />, { route: ROUTE, path: "/:section/works/:id", auth: "user" });

    await findText("연결을 확인해 주세요");
    expect(readRecentWorkIds("PIANO")).toEqual([]);
  });
});
