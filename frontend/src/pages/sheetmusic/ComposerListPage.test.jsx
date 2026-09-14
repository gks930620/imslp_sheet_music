import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ComposerListPage } from "./ComposerListPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { composerListResponse } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 04_작곡가.md 화면 A — 서버 정렬 그대로, 초성 구분 헤더는 화면에서 계산. 페이지 이동 없음.

function renderList(data = composerListResponse) {
  mockFetch([{ url: "/api/composers", data }]);
  return renderWithProviders(<ComposerListPage />, { route: "/composers", path: "/composers" });
}

describe("ComposerListPage", () => {
  it("제목 '작곡가' 와 부제 '공개된 곡이 있는 작곡가 5명'", async () => {
    renderList();
    await findText("공개된 곡이 있는 작곡가 5명");
    expect(screen.getByRole("heading", { name: /작곡가/ })).toBeInTheDocument();
  });

  it("행: 한글 표기 + 'N곡', 원어 표기 · 생몰년(en dash), 행 전체가 링크", async () => {
    renderList();
    await findText("그리그");
    const row = screen.getByRole("link", { name: /그리그/ });
    expect(row).toHaveAttribute("href", "/piano/composers/1");
    expect(row).toHaveTextContent("12곡");
    expect(row).toHaveTextContent("Grieg, Edvard · 1843–1907");
  });

  it("몰년 없으면 '1810–'", async () => {
    renderList();
    await findText("쇼팽");
    expect(screen.getByRole("link", { name: /쇼팽/ })).toHaveTextContent("Chopin, Frédéric · 1810–");
    expect(screen.getByRole("link", { name: /쇼팽/ })).not.toHaveTextContent("null");
  });

  it("초성 구분 헤더(ㄱ, ㄷ, ㅂ, ㅅ)가 있고, 한글 표기 없는 작곡가는 알파벳 헤더로 맨 뒤", async () => {
    renderList();
    await findText("그리그");
    const headers = ["ㄱ", "ㄷ", "ㅂ", "ㅅ", "S"].map((h) => screen.getByText(h));
    for (let i = 1; i < headers.length; i += 1) {
      // 앞 헤더가 문서 순서상 먼저 와야 한다
      expect(headers[i - 1].compareDocumentPosition(headers[i]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    }
  });

  it("한글 표기 없는 작곡가는 1줄에 원어, 2줄에 생몰년만", async () => {
    renderList();
    await findText("Satie, Erik");
    const row = screen.getByRole("link", { name: /Satie, Erik/ });
    expect(row).toHaveTextContent("1866–1925");
    expect(row).toHaveTextContent("3곡");
    expect(row).not.toHaveTextContent("null");
  });

  it("0명 → '아직 등록된 작곡가가 없어요' / '곡이 등록되면 작곡가도 함께 보여요'", async () => {
    renderList({ total: 0, composers: [] });
    await findText("아직 등록된 작곡가가 없어요");
    expectText("곡이 등록되면 작곡가도 함께 보여요");
  });

  it("불러오기 실패 → '연결을 확인해 주세요' + '다시 시도'", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: "/api/composers", reject: true }]);
    renderWithProviders(<ComposerListPage />, { route: "/composers", path: "/composers" });
    await findText("연결을 확인해 주세요");
    mockFetch([{ url: "/api/composers", data: composerListResponse }]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("그리그");
    expectNoText("연결을 확인해 주세요");
  });

  it("페이지 이동 UI 가 없다", async () => {
    renderList();
    await findText("그리그");
    expect(document.querySelector(".pagination")).toBeNull();
  });
});
