import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SearchBar } from "./SearchBar.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { expectNoText } from "../../test/text.js";

// 00_공통_레이아웃_토큰.md §3-1 검색창, 01_홈.md 상태별 UI(빈 검색어 Enter)
const LARGE_PLACEHOLDER = "곡 이름, 작곡가, 작품번호로 찾기 — 예: 월광, 쇼팽 녹턴, K.545";
const COMPACT_PLACEHOLDER = "곡 이름, 작곡가, 작품번호";

describe("SearchBar", () => {
  it("large 변형: 자리 문구와 '검색' 버튼", () => {
    renderWithProviders(<SearchBar variant="large" />);
    expect(screen.getByPlaceholderText(LARGE_PLACEHOLDER)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "검색" })).toBeInTheDocument();
  });

  it("기본 변형은 large", () => {
    renderWithProviders(<SearchBar />);
    expect(screen.getByPlaceholderText(LARGE_PLACEHOLDER)).toBeInTheDocument();
  });

  it("compact 변형: 자리 문구가 짧고 '검색' 버튼이 없다 (Enter 만)", () => {
    renderWithProviders(<SearchBar variant="compact" />);
    expect(screen.getByPlaceholderText(COMPACT_PLACEHOLDER)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "검색" })).not.toBeInTheDocument();
  });

  it("공백만 입력하고 Enter → 이동하지 않고 오류 문구도 없다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderWithProviders(<SearchBar />, { route: "/" });
    await user.type(screen.getByPlaceholderText(LARGE_PLACEHOLDER), "   {Enter}");
    expect(getLocation().pathname).toBe("/");
    expectNoText("입력해 주세요");
    expectNoText("오류");
  });

  it("빈 채로 '검색' 버튼 → 이동하지 않는다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderWithProviders(<SearchBar />, { route: "/" });
    await user.click(screen.getByRole("button", { name: "검색" }));
    expect(getLocation().pathname).toBe("/");
  });

  it("입력 후 Enter → /search?q=검색어 (앞뒤 공백 제거)", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderWithProviders(<SearchBar />, { route: "/" });
    await user.type(screen.getByPlaceholderText(LARGE_PLACEHOLDER), "  월광 {Enter}");
    expect(getLocation().pathname).toBe("/piano/search");
    expect(getLocation().params.get("q")).toBe("월광");
  });

  it("'검색' 버튼으로도 이동한다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderWithProviders(<SearchBar />, { route: "/" });
    await user.type(screen.getByPlaceholderText(LARGE_PLACEHOLDER), "쇼팽 녹턴");
    await user.click(screen.getByRole("button", { name: "검색" }));
    expect(getLocation().pathname).toBe("/piano/search");
    expect(getLocation().params.get("q")).toBe("쇼팽 녹턴");
  });

  it("compact 도 Enter 로 이동한다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderWithProviders(<SearchBar variant="compact" />, { route: "/works/21" });
    await user.type(screen.getByPlaceholderText(COMPACT_PLACEHOLDER), "K.545{Enter}");
    expect(getLocation().pathname).toBe("/piano/search");
    expect(getLocation().params.get("q")).toBe("K.545");
  });

  it("initialValue 로 현재 검색어를 채운다", () => {
    renderWithProviders(<SearchBar initialValue="녹턴" />);
    expect(screen.getByPlaceholderText(LARGE_PLACEHOLDER)).toHaveValue("녹턴");
  });

  it("autoFocus 면 열리자마자 커서가 검색창에 있다", () => {
    renderWithProviders(<SearchBar autoFocus />);
    expect(screen.getByPlaceholderText(LARGE_PLACEHOLDER)).toHaveFocus();
  });

  it("입력 중에는 '지우기' 버튼이 보이고 누르면 비운다", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SearchBar initialValue="녹턴" />);
    await user.click(screen.getByRole("button", { name: "지우기" }));
    expect(screen.getByPlaceholderText(LARGE_PLACEHOLDER)).toHaveValue("");
  });
});
