import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SearchResultPage } from "./SearchResultPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import { searchResponse, workNocturne2, popularWorks } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 화면정의 02_검색결과(2026-09-10 개정) · 08 §4 · 기획 04 §4·§5 · 계약 02 §0-7·§3-1
// 주소가 상태의 원본이다: q · in · section(경로) · level · pages · downloadable · page (+ 화면 전용 from)

const SEARCH = /\/api\/works\/search/;
const POPULAR = /\/api\/works\/popular/;

/** §3-1 응답 — 기존 픽스처에 이번 계약 필드 두 개(in·totalInAll)를 얹는다 */
function response({ scope = "ALL", totalInAll = null, ...rest } = {}) {
  return { ...searchResponse(rest), in: scope, totalInAll };
}

function renderSearch(route, routes) {
  mockFetch(routes);
  return renderWithProviders(<SearchResultPage />, { route, path: "/piano/search" });
}

function lastSearchParams() {
  const calls = findCalls(SEARCH);
  return calls[calls.length - 1].params;
}

function scopeRadio(name) {
  return screen.getByRole("radio", { name });
}

describe("검색 기준 — 주소에서 읽어 API 로 넘긴다", () => {
  it("in 이 없으면 '전체'가 선택돼 보이고, API 에도 in 을 보내지 않는다 (기본값은 주소에 쓰지 않는다)", async () => {
    renderSearch("/piano/search?q=녹턴", [{ url: SEARCH, data: response() }]);
    await findText("녹턴 2번");
    expect(findCall(SEARCH).params.has("in")).toBe(false);
    expect(scopeRadio("전체")).toHaveAttribute("aria-checked", "true");
  });

  it("in=TITLE 이면 그대로 넘기고 '곡명'이 선택돼 보인다", async () => {
    renderSearch("/piano/search?q=녹턴&in=TITLE", [{ url: SEARCH, data: response({ scope: "TITLE" }) }]);
    await findText("녹턴 2번");
    expect(findCall(SEARCH).params.get("in")).toBe("TITLE");
    expect(scopeRadio("곡명")).toHaveAttribute("aria-checked", "true");
  });

  it("주소의 기준 값이 이상해도 오류 화면이 아니다 — '전체'로 열린다 (인수 조건 8-F 4)", async () => {
    renderSearch("/piano/search?q=녹턴&in=xyz", [{ url: SEARCH, data: response() }]);
    await findText("녹턴 2번");
    expect(scopeRadio("전체")).toHaveAttribute("aria-checked", "true");
    expectNoText("올바르지");
    expectNoText("문제가 생겼어요");
  });

  it("구분은 경로에서 읽어 section 파라미터로 넘긴다 (02 §0-7)", async () => {
    renderSearch("/piano/search?q=녹턴", [{ url: SEARCH, data: response() }]);
    await findText("녹턴 2번");
    expect(findCall(SEARCH).params.get("section")).toBe("PIANO");
  });

  it("화면 전용 쿼리 from 은 API 로 보내지 않는다 (02 §0-5)", async () => {
    renderSearch("/piano/search?q=녹턴&from=violin", [{ url: SEARCH, data: response() }]);
    await findText("녹턴 2번");
    expect(findCall(SEARCH).params.has("from")).toBe(false);
  });
});

describe("검색 기준 — 바꿀 때 (기획 04 §4-3, 인수 조건 8-E 1·2)", () => {
  const ok = [{ url: SEARCH, data: response({ totalElements: 21, unfilteredTotal: 21 }) }];

  it("검색어는 유지하고 필터도 유지하며 페이지만 1페이지로 돌아간다", async () => {
    const { getLocation } = renderSearch(
      "/piano/search?q=녹턴&level=INTERMEDIATE&pages=LE10&downloadable=true&page=2",
      ok,
    );
    await findText("녹턴 2번");
    await userEvent.click(scopeRadio("곡명"));

    await waitFor(() => expect(getLocation().params.get("in")).toBe("TITLE"));
    const params = getLocation().params;
    expect(params.get("q")).toBe("녹턴");
    expect(params.get("level")).toBe("INTERMEDIATE");
    expect(params.get("pages")).toBe("LE10");
    expect(params.get("downloadable")).toBe("true");
    expect(params.has("page")).toBe(false);
  });

  it("'전체'로 되돌리면 주소에서 in 이 빠진다 (기본값은 주소에 남기지 않는다)", async () => {
    const { getLocation } = renderSearch("/piano/search?q=녹턴&in=TITLE", ok);
    await findText("녹턴 2번");
    await userEvent.click(scopeRadio("전체"));
    await waitFor(() => expect(getLocation().params.has("in")).toBe(false));
    expect(getLocation().params.get("q")).toBe("녹턴");
  });

  it("바꾸면 새 기준으로 다시 검색한다", async () => {
    renderSearch("/piano/search?q=녹턴", ok);
    await findText("녹턴 2번");
    await userEvent.click(scopeRadio("작곡가"));
    await waitFor(() => expect(lastSearchParams().get("in")).toBe("COMPOSER"));
  });

  it("검색창이 비어 있으면 기준만 바뀌고 화면이 이동하지 않는다 (인수 조건 8-E 2)", async () => {
    const { getLocation } = renderSearch("/piano/search", []);
    await userEvent.click(scopeRadio("곡명"));
    expect(getLocation().search).toBe("");
    expect(findCalls(SEARCH)).toHaveLength(0);
  });
});

describe("결과 건수에 기준이 드러난다 (화면정의 02 요소표)", () => {
  it("기준이 전체면 건수 줄에 기준 표시가 없다", async () => {
    renderSearch("/piano/search?q=녹턴", [
      { url: SEARCH, data: response({ totalElements: 21, unfilteredTotal: 21 }) },
    ]);
    await findText("'녹턴' 검색 결과 21곡");
    expectNoText("곡명에서 찾음");
    expectNoText("작곡가 이름에서 찾음");
  });

  it("기준이 곡명이면 '· 곡명에서 찾음'", async () => {
    renderSearch("/piano/search?q=녹턴&in=TITLE", [
      { url: SEARCH, data: response({ scope: "TITLE", totalElements: 8, unfilteredTotal: 8, totalInAll: 21 }) },
    ]);
    await findText("곡명에서 찾음");
  });

  it("기준이 작곡가면 '· 작곡가 이름에서 찾음'", async () => {
    renderSearch("/piano/search?q=쇼팽&in=COMPOSER", [
      { url: SEARCH, data: response({ scope: "COMPOSER", totalElements: 9, unfilteredTotal: 9, totalInAll: 21 }) },
    ]);
    await findText("작곡가 이름에서 찾음");
  });
});

describe("0건 화면은 A/B/C 중 하나만 그린다 (08 §4-7 D8)", () => {
  const empty = (overrides) => response({ works: [], totalElements: 0, unfilteredTotal: 0, ...overrides });

  it("[A] 필터가 걸려 있으면 필터 안내만 — 기준 이야기도 인기곡도 없다", async () => {
    renderSearch("/piano/search?q=녹턴&in=TITLE&level=BEGINNER", [
      { url: SEARCH, data: empty({ scope: "TITLE", unfilteredTotal: 21, totalInAll: 21 }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("이 조건에 맞는 곡이 없어요");
    expectText("필터 해제");
    expectNoText("전체로 찾기");
    expectNoText("곡명에서만 찾고 있어요");
    expectNoText("인기곡");
  });

  it("[B] 필터 없음 + 기준이 곡명 + 전체 기준에 결과가 있으면 '전체로 찾기' 하나만", async () => {
    renderSearch("/piano/search?q=쇼팽 녹턴&in=TITLE", [
      { url: SEARCH, data: empty({ q: "쇼팽 녹턴", scope: "TITLE", totalInAll: 21 }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("'쇼팽 녹턴'을 곡명에서 찾지 못했어요");
    expectText("전체에서 찾으면 21곡이 있어요");
    expect(screen.getByRole("button", { name: /전체로 찾기/ })).toBeInTheDocument();
    // 여기 없는 것 — 확실한 출구 하나를 조언 더미에 묻지 않는다
    expectNoText("다른 이름으로 불리기도 해요");
    expectNoText("IMSLP 에서 직접 찾아보기");
    expectNoText("인기곡");
    expectNoText("작곡가 목록 보기");
  });

  it("[B] 의 '전체로 찾기'를 누르면 기준이 전체로 바뀌고 같은 검색어로 다시 찾는다 (인수 조건 8-E 3)", async () => {
    const { getLocation } = renderSearch("/piano/search?q=쇼팽 녹턴&in=TITLE", [
      { url: SEARCH, data: empty({ q: "쇼팽 녹턴", scope: "TITLE", totalInAll: 21 }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("전체에서 찾으면 21곡이 있어요");
    await userEvent.click(screen.getByRole("button", { name: /전체로 찾기/ }));
    await waitFor(() => expect(getLocation().params.has("in")).toBe(false));
    expect(getLocation().params.get("q")).toBe("쇼팽 녹턴");
  });

  it("[B] 는 기준이 작곡가일 때 문구가 바뀐다", async () => {
    renderSearch("/piano/search?q=녹턴&in=COMPOSER", [
      { url: SEARCH, data: empty({ scope: "COMPOSER", totalInAll: 21 }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("'녹턴'을 작곡가 이름에서 찾지 못했어요");
  });

  it("[C] 전체 기준으로도 0건이면(totalInAll=0) '전체로 찾기'를 띄우지 않는다 (인수 조건 8-E 4)", async () => {
    renderSearch("/piano/search?q=ㅁㄴㅇ&in=TITLE", [
      { url: SEARCH, data: empty({ q: "ㅁㄴㅇ", scope: "TITLE", totalInAll: 0 }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("'ㅁㄴㅇ'에 맞는 곡을 찾지 못했어요");
    expectNoText("전체로 찾기");
    expectNoText("전체에서 찾으면");
  });

  it("[C] 알 수 없을 때(totalInAll 이 null)도 [B] 로 가지 않는다 — 추측으로 출구를 띄우지 않는다", async () => {
    renderSearch("/piano/search?q=ㅁㄴㅇ&in=TITLE", [
      { url: SEARCH, data: empty({ q: "ㅁㄴㅇ", scope: "TITLE", totalInAll: null }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("'ㅁㄴㅇ'에 맞는 곡을 찾지 못했어요");
    expectNoText("전체로 찾기");
  });

  it("[C] 기준이 전체가 아니면 사실 고지 줄이 붙는다 — 버튼이 아니다 (08 §4-7)", async () => {
    renderSearch("/piano/search?q=ㅁㄴㅇ&in=TITLE", [
      { url: SEARCH, data: empty({ q: "ㅁㄴㅇ", scope: "TITLE", totalInAll: 0 }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("지금은 '곡명'에서만 찾고 있어요");
    expect(screen.queryByRole("button", { name: /곡명에서만/ })).not.toBeInTheDocument();
  });

  it("[C] 기준이 전체면 사실 고지 줄이 없고, 좁히라는 안내도 없다 (인수 조건 8-E 5)", async () => {
    renderSearch("/piano/search?q=ㅁㄴㅇ", [
      { url: SEARCH, data: empty({ q: "ㅁㄴㅇ" }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("'ㅁㄴㅇ'에 맞는 곡을 찾지 못했어요");
    expectNoText("에서만 찾고 있어요");
    expectNoText("좁혀");
    expectText("다른 이름으로 불리기도 해요");
  });

  it("[C] 편성 안내 줄이 상단 탭과 같은 말을 한다 (기획 04 §7 충돌 2, 인수 조건 8-E 6)", async () => {
    renderSearch("/piano/search?q=ㅁㄴㅇ", [
      { url: SEARCH, data: empty({ q: "ㅁㄴㅇ" }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("지금은 피아노 악보만 있어요 (바이올린·오케스트라는 준비 중)");
    expectNoText("1차는 피아노 독주곡만 있어요");
  });
});

describe("준비 중 구분에서 헤더 검색으로 왔을 때 (08 §2-4, 인수 조건 8-A 6)", () => {
  it("결과 위에 '바이올린은 준비 중이라 피아노 악보에서 찾았어요.' 한 줄", async () => {
    renderSearch("/piano/search?q=녹턴&from=violin", [{ url: SEARCH, data: response() }]);
    await findText("바이올린은 준비 중이라 피아노 악보에서 찾았어요.");
  });

  it("오케스트라도 같은 자리에 고정 문구로 (조사를 계산하지 않는다)", async () => {
    renderSearch("/piano/search?q=녹턴&from=orchestra", [{ url: SEARCH, data: response() }]);
    await findText("오케스트라는 준비 중이라 피아노 악보에서 찾았어요.");
  });

  it("from 이 없거나 알 수 없는 값이면 줄이 없다 — 오류로 만들지 않는다", async () => {
    renderSearch("/piano/search?q=녹턴&from=cello", [{ url: SEARCH, data: response() }]);
    await findText("녹턴 2번");
    expectNoText("피아노 악보에서 찾았어요");
  });

  it("0건이어도 이 줄은 그대로 보인다 (08 §2-4 — 0건 안내와 겹치는 말을 하지 않는다)", async () => {
    renderSearch("/piano/search?q=ㅁㄴㅇ&from=violin", [
      { url: SEARCH, data: response({ q: "ㅁㄴㅇ", works: [], totalElements: 0, unfilteredTotal: 0 }) },
      { url: POPULAR, data: popularWorks(5) },
    ]);
    await findText("바이올린은 준비 중이라 피아노 악보에서 찾았어요.");
    expectText("'ㅁㄴㅇ'에 맞는 곡을 찾지 못했어요");
  });

  it("기준을 바꾸면 그 줄은 사라진다 — 이 검색 한 번에만 보인다", async () => {
    const { getLocation } = renderSearch("/piano/search?q=녹턴&from=violin", [
      { url: SEARCH, data: response() },
    ]);
    await findText("바이올린은 준비 중이라 피아노 악보에서 찾았어요.");
    await userEvent.click(scopeRadio("곡명"));
    await waitFor(() => expect(getLocation().params.has("from")).toBe(false));
  });
});

describe("곡 카드·작곡가 카드 링크는 구분 아래로 간다 (기획 04 §3-2)", () => {
  it("곡 카드 → /piano/works/:id, 작곡가 카드 → /piano/composers/:id", async () => {
    renderSearch("/piano/search?q=쇼팽", [
      {
        url: SEARCH,
        data: response({
          q: "쇼팽",
          works: [workNocturne2],
          composers: [{ id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", workCount: 24 }],
          composerMatchCount: 1,
        }),
      },
    ]);
    await findText("녹턴 2번");
    expect(screen.getByRole("link", { name: /녹턴 2번/ })).toHaveAttribute("href", "/piano/works/23");
    expect(screen.getByRole("link", { name: /작곡가: 쇼팽/ })).toHaveAttribute("href", "/piano/composers/9");
  });
});

describe("작곡가 일치 카드와 기준 (인수 조건 8-D 5·9)", () => {
  const chopinCard = { id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", workCount: 24 };

  it.each(["ALL", "COMPOSER"])("기준이 %s 면 작곡가 카드가 보인다", async (scope) => {
    const route = scope === "ALL" ? "/piano/search?q=쇼팽" : "/piano/search?q=쇼팽&in=COMPOSER";
    renderSearch(route, [
      { url: SEARCH, data: response({ q: "쇼팽", scope, composers: [chopinCard], composerMatchCount: 1 }) },
    ]);
    await findText("작곡가: 쇼팽");
  });

  /**
   * designer 가 화면에도 맡긴 규칙이다 — 서버는 in=TITLE 에서 composers 를 빈 배열로 준다(02 §3-1).
   * 그래도 화면이 한 겹 더 막는다: 이 카드가 뜨는 순간 "곡명 기준은 작곡가를 찾지 않는다" 는 약속이
   * 사용자 눈앞에서 깨져 보이기 때문이다(화면정의 02 요소표).
   */
  it("기준이 곡명이면 서버가 카드를 실어 보내도 화면에 그리지 않는다", async () => {
    renderSearch("/piano/search?q=쇼팽&in=TITLE", [
      { url: SEARCH, data: response({ q: "쇼팽", scope: "TITLE", composers: [chopinCard], composerMatchCount: 1 }) },
    ]);
    await findText("녹턴 2번");
    expectNoText("작곡가: 쇼팽");
    expectNoText("등록된 곡 24개");
  });
});
