import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { HomePage } from "./HomePage.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../test/apiMock.js";
import { featuredComposers, popularWorks, workSummary } from "../test/fixtures.js";
import { expectNoText, findText } from "../test/text.js";

/**
 * 홈의 "최근 본 곡" — 화면정의 01_홈(2026-09-20 개정), 기획 05 §4, 인수 조건 8-E 1~11 · 8-G 4.
 * 계약: 02 §3-9(`GET /api/works/recent`) · 03_기술결정 §23(브라우저 저장).
 *
 * <b>약속은 "첫 방문 홈은 한 글자도 달라지지 않는다"</b> 이다 — 저장된 곡이 없으면 영역도 요청도 없다.
 */

const RECENT_WORKS_KEY = "sheetmusic.recentWorks";

function givenRecent(ids, section = "PIANO") {
  localStorage.setItem(RECENT_WORKS_KEY, JSON.stringify({ [section]: ids }));
}

function renderHome(routes = []) {
  mockFetch([
    { url: /\/api\/works\/popular/, data: popularWorks(10) },
    { url: /\/api\/composers\/featured/, data: featuredComposers(8) },
    { url: /\/api\/works\/recent/, data: [] },
    ...routes,
  ]);
  return renderWithProviders(<HomePage />, { route: "/piano", path: "/:section" });
}

function recentSection() {
  return screen.queryByRole("heading", { name: "최근 본 곡" })?.closest("section") ?? null;
}

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe("저장된 곡이 없을 때 — 첫 방문·시크릿 창 (8-E 2·10, 8-G 4)", () => {
  it("영역도 요청도 없다. 인기곡 10개·작곡가 8명은 그대로다", async () => {
    renderHome();
    await findText("인기곡");

    expect(recentSection()).toBeNull();
    expectNoText("최근 본 곡");
    expect(findCalls(/\/api\/works\/recent/)).toHaveLength(0);
    expect(await screen.findAllByRole("link", { name: /인기곡 \d+/ })).toHaveLength(10);
    expect(screen.getAllByRole("link", { name: /\d+곡/ })).toHaveLength(8);
  });
});

describe("저장된 곡이 있을 때 (8-E 1·7)", () => {
  it("저장한 순서 그대로 '지금 정보' 를 물어 그린다 — 검색창 아래·인기곡 위", async () => {
    givenRecent([23, 21]);
    renderHome([
      {
        url: /\/api\/works\/recent/,
        data: [
          workSummary({ id: 23, titleKo: "녹턴 2번", titleOriginal: "Nocturne Op.9 No.2" }),
          workSummary({ id: 21, titleKo: "월광 소나타" }),
        ],
      },
    ]);
    await findText("최근 본 곡");

    const call = findCalls(/\/api\/works\/recent/)[0];
    expect(call.params.get("ids")).toBe("23,21");
    expect(call.params.get("section")).toBe("PIANO");

    const section = recentSection();
    const cards = within(section).getAllByRole("link");
    expect(cards[0]).toHaveAttribute("href", "/piano/works/23");
    expect(cards[0]).toHaveTextContent("녹턴 2번");

    // 순서: 검색창 → 최근 본 곡 → 인기곡
    const popular = screen.getByRole("heading", { name: /인기곡/ }).closest("section");
    expect(section.compareDocumentPosition(popular) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("항목에 순위 숫자가 없다 — 인기곡과 같은 카드에서 순위만 뺀 것 (8-E 7)", async () => {
    givenRecent([23]);
    renderHome([{ url: /\/api\/works\/recent/, data: [workSummary({ id: 23, titleKo: "녹턴 2번" })] }]);
    await findText("최근 본 곡");

    expect(recentSection().querySelectorAll(".work-card-rank")).toHaveLength(0);
  });

  it("'지우기' 를 누르면 확인 창 없이 즉시 영역이 사라지고 저장도 비워진다 (8-E 6)", async () => {
    const user = userEvent.setup();
    givenRecent([23]);
    renderHome([{ url: /\/api\/works\/recent/, data: [workSummary({ id: 23, titleKo: "녹턴 2번" })] }]);
    await findText("최근 본 곡");

    await user.click(screen.getByRole("button", { name: "최근 본 곡 지우기" }));

    expect(recentSection()).toBeNull();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem(RECENT_WORKS_KEY) ?? "{}").PIANO ?? []).toEqual([]);
    expectNoText("되돌리기");
  });
});

describe("보여줄 것이 없을 때 — 조용히 숨는다 (8-E 8·11)", () => {
  it("정보를 못 가져오면 영역이 조용히 사라진다. 오류 상자·다시 시도 없음", async () => {
    givenRecent([23, 21]);
    renderHome([{ url: /\/api\/works\/recent/, reject: true }]);
    await findText("인기곡");

    await waitFor(() => expect(recentSection()).toBeNull());
    expectNoText("최근 본 곡");
    expectNoText("연결을 확인해 주세요");
  });

  it("숨김·삭제로 전부 빠지면 영역이 없다", async () => {
    givenRecent([23, 21]);
    renderHome([{ url: /\/api\/works\/recent/, data: [] }]);
    await findText("인기곡");

    await waitFor(() => expect(recentSection()).toBeNull());
  });

  it("일부만 빠지면 남은 것만 보인다 — 눌러서 404 를 만나지 않는다", async () => {
    givenRecent([23, 21, 22]);
    renderHome([{ url: /\/api\/works\/recent/, data: [workSummary({ id: 21, titleKo: "월광 소나타" })] }]);
    await findText("최근 본 곡");

    const cards = within(recentSection()).getAllByRole("link");
    expect(cards).toHaveLength(1);
    expect(cards[0]).toHaveAttribute("href", "/piano/works/21");
  });
});
