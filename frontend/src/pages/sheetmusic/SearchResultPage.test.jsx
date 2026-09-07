import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SearchResultPage } from "./SearchResultPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import { searchResponse, workNocturne2, workSummary, popularWorks } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 02_검색결과.md — URL 쿼리(q, level, pages, downloadable, page)가 상태의 원본. 필터는 즉시 URL 갱신 + page=0.
const PLACEHOLDER = "곡 이름, 작곡가, 작품번호로 찾기 — 예: 월광, 쇼팽 녹턴, K.545";
const SEARCH = /\/api\/works\/search/;

function renderSearch(route, routes) {
  mockFetch(routes);
  return renderWithProviders(<SearchResultPage />, { route, path: "/search" });
}

function lastSearchParams() {
  const calls = findCalls(SEARCH);
  return calls[calls.length - 1].params;
}

describe("SearchResultPage — 요청·건수", () => {
  it("URL 쿼리를 그대로 API 에 전달하고, 필터 UI 에 반영한다", async () => {
    renderSearch("/search?q=녹턴&level=ELEMENTARY,INTERMEDIATE&pages=LE10&downloadable=true&page=1", [
      { url: SEARCH, data: searchResponse({ works: [workNocturne2], totalElements: 8, unfilteredTotal: 21, page: 1 }) },
    ]);
    await findText("녹턴 2번");
    const params = findCall(SEARCH).params;
    expect(params.get("q")).toBe("녹턴");
    expect(params.get("level")).toBe("ELEMENTARY,INTERMEDIATE");
    expect(params.get("pages")).toBe("LE10");
    expect(params.get("downloadable")).toBe("true");
    expect(params.get("page")).toBe("1");
    expect(params.has("size")).toBe(false);

    expect(screen.getByRole("button", { name: "초급", pressed: true })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "중급", pressed: true })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "입문", pressed: false })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "10쪽 이하", pressed: true })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "바로 받기 가능한 곡만" })).toBeChecked();
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveValue("녹턴");
  });

  it("결과 건수: 필터 없으면 \"'녹턴' 검색 결과 21곡\"", async () => {
    renderSearch("/search?q=녹턴", [{ url: SEARCH, data: searchResponse({ totalElements: 21, unfilteredTotal: 21 }) }]);
    await findText("'녹턴' 검색 결과 21곡");
    expectNoText("곡 중");
  });

  it("필터가 걸려 있으면 \"'녹턴' 검색 결과 21곡 중 8곡\"", async () => {
    renderSearch("/search?q=녹턴&level=INTERMEDIATE", [
      { url: SEARCH, data: searchResponse({ totalElements: 8, unfilteredTotal: 21 }) },
    ]);
    await findText("'녹턴' 검색 결과 21곡 중 8곡");
  });

  it("난이도 칩에 한 줄 기준 툴팁", async () => {
    renderSearch("/search?q=녹턴", [{ url: SEARCH, data: searchResponse() }]);
    await findText("녹턴 2번");
    expect(screen.getByRole("button", { name: "입문" })).toHaveAttribute("title", "바이엘 수준");
    expect(screen.getByRole("button", { name: "초급" })).toHaveAttribute("title", "체르니 30 수준");
    expect(screen.getByRole("button", { name: "중급" })).toHaveAttribute("title", "체르니 40·소나티네 수준");
    expect(screen.getByRole("button", { name: "고급" })).toHaveAttribute("title", "체르니 50 이상·연주회 레퍼토리");
  });
});

describe("SearchResultPage — 작곡가 카드·곡 카드", () => {
  it("검색어가 작곡가에 걸리면 맨 위에 작곡가 카드, 누르면 작곡가 상세", async () => {
    renderSearch("/search?q=쇼팽", [
      {
        url: SEARCH,
        data: searchResponse({
          q: "쇼팽",
          composers: [{ id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", workCount: 24 }],
          composerMatchCount: 1,
        }),
      },
    ]);
    await findText("작곡가: 쇼팽 (Chopin, Frédéric)");
    const card = screen.getByRole("link", { name: /작곡가: 쇼팽/ });
    expect(card).toHaveAttribute("href", "/composers/9");
    expect(card).toHaveTextContent("곡 24개");
  });

  it("작곡가가 3명을 넘으면 '작곡가 N명 더 — 작곡가 목록에서 찾기' 링크", async () => {
    renderSearch("/search?q=바", [
      {
        url: SEARCH,
        data: searchResponse({
          q: "바",
          composers: [
            { id: 1, nameKo: "바흐", nameOriginal: "Bach, Johann Sebastian", workCount: 15 },
            { id: 2, nameKo: "바르톡", nameOriginal: "Bartók, Béla", workCount: 4 },
            { id: 3, nameKo: "바버", nameOriginal: "Barber, Samuel", workCount: 2 },
          ],
          composerMatchCount: 5,
        }),
      },
    ]);
    await findText("작곡가 2명 더 — 작곡가 목록에서 찾기");
    expect(screen.getByRole("link", { name: /작곡가 목록에서 찾기/ })).toHaveAttribute("href", "/composers");
  });

  it("작곡가에 안 걸리면 카드 없음", async () => {
    renderSearch("/search?q=녹턴", [{ url: SEARCH, data: searchResponse() }]);
    await findText("녹턴 2번");
    expectNoText("작곡가:");
  });

  it("곡 카드에 별칭 일치 줄·상태 뱃지가 보인다", async () => {
    renderSearch("/search?q=녹턴", [{ url: SEARCH, data: searchResponse() }]);
    await findText("녹턴 2번");
    expect(screen.getByText("'녹턴'으로 찾음")).toBeInTheDocument();
    expect(screen.getByText("준비 중")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /녹턴 2번/ })).toHaveAttribute("href", "/works/23");
  });
});

describe("SearchResultPage — 필터 ↔ URL", () => {
  const ok = [{ url: SEARCH, data: searchResponse({ totalElements: 21, unfilteredTotal: 21 }) }];

  it("난이도 칩은 복수 선택 → level=A,B, 바꾸면 page=0", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴&page=1", ok);
    await findText("녹턴 2번");
    await user.click(screen.getByRole("button", { name: "초급" }));
    expect(getLocation().params.get("level")).toBe("ELEMENTARY");
    expect(getLocation().params.get("page") ?? "0").toBe("0");
    await user.click(screen.getByRole("button", { name: "중급" }));
    expect(getLocation().params.get("level")).toBe("ELEMENTARY,INTERMEDIATE");
    await user.click(screen.getByRole("button", { name: "초급" }));
    expect(getLocation().params.get("level")).toBe("INTERMEDIATE");
    expect(getLocation().params.get("q")).toBe("녹턴");
    await waitFor(() => expect(lastSearchParams().get("level")).toBe("INTERMEDIATE"));
  });

  it("쪽수 칩은 단일 선택 → pages=LE10|11_20|GE21", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴", ok);
    await findText("녹턴 2번");
    await user.click(screen.getByRole("button", { name: "10쪽 이하" }));
    expect(getLocation().params.get("pages")).toBe("LE10");
    await user.click(screen.getByRole("button", { name: "11~20쪽" }));
    expect(getLocation().params.get("pages")).toBe("11_20");
    await user.click(screen.getByRole("button", { name: "21쪽 이상" }));
    expect(getLocation().params.get("pages")).toBe("GE21");
    await user.click(screen.getByRole("button", { name: "21쪽 이상" }));
    expect(getLocation().params.has("pages")).toBe(false);
  });

  it("'바로 받기 가능한 곡만' 체크 → downloadable=true, 해제하면 쿼리 제거", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴", ok);
    await findText("녹턴 2번");
    await user.click(screen.getByRole("checkbox", { name: "바로 받기 가능한 곡만" }));
    expect(getLocation().params.get("downloadable")).toBe("true");
    await user.click(screen.getByRole("checkbox", { name: "바로 받기 가능한 곡만" }));
    expect(getLocation().params.has("downloadable")).toBe(false);
  });

  it("'필터 해제' 는 필터가 있을 때만 보이고, 누르면 필터 쿼리만 지운다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴", ok);
    await findText("녹턴 2번");
    expect(screen.queryByRole("button", { name: "필터 해제" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "고급" }));
    await user.click(screen.getByRole("button", { name: "필터 해제" }));
    expect(getLocation().params.has("level")).toBe(false);
    expect(getLocation().params.get("q")).toBe("녹턴");
  });

  it("페이지 이동 → page 쿼리 갱신", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴", ok);
    await findText("녹턴 2번");
    await user.click(screen.getByRole("button", { name: "2" }));
    expect(getLocation().params.get("page")).toBe("1");
    await waitFor(() => expect(lastSearchParams().get("page")).toBe("1"));
  });

  it("검색창에서 새 검색 → q 갱신, 필터·page 초기화", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴&level=ADVANCED&page=1", ok);
    await findText("녹턴 2번");
    const input = screen.getByPlaceholderText(PLACEHOLDER);
    await user.clear(input);
    await user.type(input, "월광{Enter}");
    expect(getLocation().pathname).toBe("/search");
    expect(getLocation().params.get("q")).toBe("월광");
    expect(getLocation().params.has("level")).toBe(false);
    expect(getLocation().params.has("page")).toBe(false);
  });
});

describe("SearchResultPage — 빈 상태·오류", () => {
  it("q 없이 진입 → 안내 문구 + 검색창 포커스, 검색 API 호출 없음", async () => {
    renderSearch("/search", [{ url: SEARCH, data: searchResponse() }]);
    expect(screen.getByText("찾고 싶은 곡 이름, 작곡가, 작품번호를 입력해 주세요")).toBeInTheDocument();
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveFocus();
    expect(screen.queryByRole("checkbox", { name: "바로 받기 가능한 곡만" })).not.toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 20));
    expect(findCalls(SEARCH)).toHaveLength(0);
  });

  it("결과 0건(필터 없음) → 찾지 못했어요 안내 3줄 + 인기곡 5개 + 작곡가 목록 링크, 필터 바 숨김", async () => {
    renderSearch("/search?q=ㅁㄴㅇ", [
      { url: SEARCH, data: searchResponse({ q: "ㅁㄴㅇ", works: [], totalElements: 0, unfilteredTotal: 0 }) },
      { url: "/api/works/popular", data: popularWorks(5) },
    ]);
    await findText("'ㅁㄴㅇ'에 맞는 곡을 찾지 못했어요");
    expectText("다른 이름으로 불리기도 해요 — 예: 월광 / Moonlight / Op.27 No.2");
    expectText("작곡가 이름으로 찾아보세요");
    expectText("1차는 피아노 독주곡만 있어요");
    expect(screen.getByRole("link", { name: "작곡가 목록 보기" })).toHaveAttribute("href", "/composers");
    expect(screen.queryByRole("checkbox", { name: "바로 받기 가능한 곡만" })).not.toBeInTheDocument();
    await findText("인기곡 5");
    expect(findCall("/api/works/popular").params.get("limit")).toBe("5");
    expect(screen.getAllByRole("link").filter((a) => /^\/works\/\d+$/.test(a.getAttribute("href")))).toHaveLength(5);
  });

  it("필터를 걸어 0건 → '이 조건에 맞는 곡이 없어요' + '필터 해제'(필터 바 유지)", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴&level=BEGINNER&downloadable=true", [
      { url: SEARCH, data: searchResponse({ works: [], totalElements: 0, unfilteredTotal: 21 }) },
    ]);
    await findText("이 조건에 맞는 곡이 없어요");
    expectNoText("찾지 못했어요");
    expect(screen.getByRole("checkbox", { name: "바로 받기 가능한 곡만" })).toBeChecked();
    await user.click(screen.getByRole("button", { name: "필터 해제" }));
    expect(getLocation().params.has("level")).toBe(false);
    expect(getLocation().params.has("downloadable")).toBe(false);
    expect(getLocation().params.get("q")).toBe("녹턴");
  });

  it("작곡가 카드만 있고 곡 0건 → 카드 + 빈 상태", async () => {
    renderSearch("/search?q=쇼팽", [
      {
        url: SEARCH,
        data: searchResponse({
          q: "쇼팽",
          works: [],
          totalElements: 0,
          unfilteredTotal: 0,
          composers: [{ id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", workCount: 0 }],
          composerMatchCount: 1,
        }),
      },
      { url: "/api/works/popular", data: [] },
    ]);
    await findText("'쇼팽'에 맞는 곡을 찾지 못했어요");
    expect(screen.getByRole("link", { name: /작곡가: 쇼팽/ })).toBeInTheDocument();
  });

  it("페이지 범위 밖(빈 content, total>0) → '이 페이지에는 곡이 없어요' + '첫 페이지로'", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderSearch("/search?q=녹턴&page=99", [
      { url: SEARCH, data: searchResponse({ works: [], totalElements: 21, unfilteredTotal: 21, page: 99 }) },
    ]);
    await findText("이 페이지에는 곡이 없어요");
    await user.click(screen.getByRole("button", { name: "첫 페이지로" }));
    expect(getLocation().params.get("page") ?? "0").toBe("0");
  });

  it("불러오기 실패 → '연결을 확인해 주세요' + '다시 시도'(재요청), 검색창은 살아 있음", async () => {
    const user = userEvent.setup();
    renderSearch("/search?q=녹턴", [{ url: SEARCH, reject: true }]);
    await findText("연결을 확인해 주세요");
    expectText("잠시 후 다시 시도해 주세요");
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toBeInTheDocument();
    mockFetch([{ url: SEARCH, data: searchResponse() }]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("녹턴 2번");
  });

  it("한국어 제목 없는 곡은 원어 제목이 제목 자리에 오고 '저작권 확인 중' 뱃지", async () => {
    renderSearch("/search?q=Nocturnes", [
      {
        url: SEARCH,
        data: searchResponse({
          q: "Nocturnes",
          works: [workSummary({ id: 30, titleKo: null, titleOriginal: "Nocturnes, Op.9", status: "UNKNOWN", level: null, pageCount: null, matchedAlias: null })],
        }),
      },
    ]);
    await findText("Nocturnes, Op.9");
    expect(screen.getAllByText("Nocturnes, Op.9")).toHaveLength(1);
    expect(screen.getByText("저작권 확인 중")).toBeInTheDocument();
    expect(screen.getByText("난이도 미정")).toBeInTheDocument();
  });
});
