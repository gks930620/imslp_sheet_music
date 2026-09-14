import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HomePage } from "./HomePage.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { mockFetch, findCall } from "../test/apiMock.js";
import { popularWorks, featuredComposers } from "../test/fixtures.js";
import { expectNoText, findText } from "../test/text.js";

// 화면정의 01_홈(2026-09-10 개정) · 08 §4-3·§4-5 · 기획 04 §4-3
// 홈에서 기준을 고르는 것만으로는 검색이 일어나지 않는다 — 자리 문구와 예시 칩만 바뀐다.

const PLACEHOLDER = {
  ALL: "곡 이름, 작곡가, 작품번호로 찾기 — 예: 월광, 쇼팽 녹턴, K.545",
  TITLE: "곡 이름으로 찾기 — 예: 월광, 엘리제를 위하여, Op.27 No.2",
  COMPOSER: "작곡가 이름으로 찾기 — 예: 쇼팽, 베토벤, Chopin",
};

function renderHome(route = "/piano") {
  mockFetch([
    { url: /\/api\/works\/popular/, data: popularWorks(10) },
    { url: /\/api\/composers\/featured/, data: featuredComposers(8) },
  ]);
  return renderWithProviders(<HomePage />, { route });
}

describe("홈 — 검색 기준 세그먼트", () => {
  it("검색창 옆에 전체·곡명·작곡가가 있고 처음 열면 '전체'가 골라져 있다 (인수 조건 8-D 1)", async () => {
    renderHome();
    const group = screen.getByRole("radiogroup", { name: "검색 기준" });
    expect(group).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "전체" })).toHaveAttribute("aria-checked", "true");
  });

  it.each([
    ["전체", PLACEHOLDER.ALL],
    ["곡명", PLACEHOLDER.TITLE],
    ["작곡가", PLACEHOLDER.COMPOSER],
  ])("기준 '%s' 를 고르면 자리 문구가 바뀐다 (08 §4-3)", async (label, placeholder) => {
    renderHome();
    if (label !== "전체") {
      await userEvent.click(screen.getByRole("radio", { name: label }));
    }
    await waitFor(() => expect(screen.getByPlaceholderText(placeholder)).toBeInTheDocument());
  });

  it("기준만 바꾸면 화면이 이동하지 않고 오류 문구도 없다 (인수 조건 8-E 2)", async () => {
    const { getLocation } = renderHome();
    await userEvent.click(screen.getByRole("radio", { name: "작곡가" }));
    expect(getLocation().pathname).toBe("/piano");
    expect(getLocation().search).toBe("");
    expectNoText("입력해 주세요");
  });

  it("기준을 바꿔도 이미 친 글자를 지우지 않는다 (08 §4-3)", async () => {
    renderHome();
    const input = screen.getByPlaceholderText(PLACEHOLDER.ALL);
    await userEvent.type(input, "월광");
    await userEvent.click(screen.getByRole("radio", { name: "곡명" }));
    expect(screen.getByPlaceholderText(PLACEHOLDER.TITLE)).toHaveValue("월광");
  });
});

describe("홈 — 예시 칩은 기준을 따라 바뀐다 (08 §4-5 D9)", () => {
  it.each([
    ["전체", ["월광", "쇼팽 녹턴", "K.545"]],
    ["곡명", ["월광", "엘리제를 위하여", "Op.27 No.2"]],
    ["작곡가", ["쇼팽", "베토벤", "모차르트"]],
  ])("기준 '%s' 의 칩 3개", async (label, chips) => {
    renderHome();
    if (label !== "전체") {
      await userEvent.click(screen.getByRole("radio", { name: label }));
    }
    for (const chip of chips) {
      await waitFor(() => expect(screen.getByRole("button", { name: chip })).toBeInTheDocument());
    }
  });

  it("칩을 누르면 현재 기준 그대로 검색한다 — 칩이 기준을 바꾸지 않는다", async () => {
    const { getLocation } = renderHome();
    await userEvent.click(screen.getByRole("radio", { name: "곡명" }));
    await userEvent.click(await screen.findByRole("button", { name: "엘리제를 위하여" }));

    await waitFor(() => expect(getLocation().pathname).toBe("/piano/search"));
    expect(getLocation().params.get("q")).toBe("엘리제를 위하여");
    expect(getLocation().params.get("in")).toBe("TITLE");
  });

  it("기준이 전체면 칩이 만드는 주소에 in 이 없다", async () => {
    const { getLocation } = renderHome();
    await userEvent.click(screen.getByRole("button", { name: "월광" }));
    await waitFor(() => expect(getLocation().pathname).toBe("/piano/search"));
    expect(getLocation().params.has("in")).toBe(false);
  });
});

describe("홈 — 검색 제출", () => {
  it("Enter 로 검색하면 현재 구분의 검색 결과로 가고 기준이 실린다", async () => {
    const { getLocation } = renderHome();
    await userEvent.click(screen.getByRole("radio", { name: "작곡가" }));
    await userEvent.type(screen.getByPlaceholderText(PLACEHOLDER.COMPOSER), "쇼팽{Enter}");

    await waitFor(() => expect(getLocation().pathname).toBe("/piano/search"));
    expect(getLocation().params.get("q")).toBe("쇼팽");
    expect(getLocation().params.get("in")).toBe("COMPOSER");
  });
});

describe("홈 — 인기곡·작곡가는 현재 구분의 것만 (기획 04 §5)", () => {
  it("두 요청 모두 section=PIANO 를 싣는다", async () => {
    renderHome();
    await findText("인기곡 1");
    expect(findCall(/\/api\/works\/popular/).params.get("section")).toBe("PIANO");
    expect(findCall(/\/api\/composers\/featured/).params.get("section")).toBe("PIANO");
  });

  it("'모든 작곡가 보기'와 곡 카드는 구분 아래 주소로 간다", async () => {
    renderHome();
    await findText("인기곡 1");
    expect(screen.getByRole("link", { name: /모든 작곡가 보기/ })).toHaveAttribute("href", "/piano/composers");
    expect(screen.getAllByRole("link", { name: /인기곡 1/ })[0]).toHaveAttribute("href", "/piano/works/100");
  });
});
