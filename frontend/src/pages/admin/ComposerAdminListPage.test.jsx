import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ComposerAdminListPage } from "./ComposerAdminListPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../test/apiMock.js";
import { adminComposer, adminComposers, pageResponse } from "../../test/fixtures.js";
import { expectNoText, findText } from "../../test/text.js";

// 05_관리자_홈_및_작곡가곡관리.md 화면 B — /admin/composers
// GET /api/admin/composers?q=&missingKo=&page= (02_API §4-2 → PageResponse<AdminComposerDTO>)
// URL 쿼리가 상태의 원본. 검색·필터를 바꾸면 page 쿼리를 지운다(= 0쪽, lib/workQuery.js 관례와 동일).
const LIST = /\/api\/admin\/composers(\?|$)/;
const PLACEHOLDER = "이름으로 찾기";
const MISSING_KO = "한글 표기 없는 작곡가만";

function renderPage(route = "/admin/composers", routes) {
  mockFetch(routes ?? [{ url: LIST, data: pageResponse(adminComposers) }]);
  return renderWithProviders(<ComposerAdminListPage />, { route, path: "/admin/composers", auth: "admin" });
}

function lastParams() {
  const calls = findCalls(LIST);
  return calls[calls.length - 1].params;
}

function row(id) {
  return screen.getByTestId(`admin-composer-row-${id}`);
}

describe("ComposerAdminListPage — 머리말·표", () => {
  it("브레드크럼 '관리 › 작곡가 관리', 제목, '새 작곡가' 버튼", async () => {
    renderPage();
    await findText("베토벤");
    expect(screen.getByRole("heading", { name: /작곡가 관리/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "관리" })).toHaveAttribute("href", "/admin");
    expect(screen.getByRole("link", { name: /새 작곡가/ })).toHaveAttribute("href", "/admin/composers/new");
  });

  it("표 열 머리: 한글 표기 / 원어 표기 / 생몰년 / 곡 수", async () => {
    renderPage();
    await findText("베토벤");
    for (const name of ["한글 표기", "원어 표기", "생몰년", "곡 수"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
  });

  it("행: 한글 표기·원어 표기·생몰년·곡 수 + '수정' 링크", async () => {
    renderPage();
    await findText("베토벤");
    const beethoven = row(4);
    expect(beethoven).toHaveTextContent("베토벤");
    expect(beethoven).toHaveTextContent("Beethoven, Ludwig van");
    expect(beethoven).toHaveTextContent("1770–1827");
    expect(beethoven).toHaveTextContent("18");
    expect(within(beethoven).getByRole("link", { name: "수정" })).toHaveAttribute("href", "/admin/composers/4");
  });

  it("한글 표기가 없으면 '(없음)' 으로 보이고 'null' 이 새지 않는다", async () => {
    renderPage();
    await findText("Satie, Erik");
    const satie = row(11);
    expect(within(satie).getByText("(없음)")).toBeInTheDocument();
    expect(satie).not.toHaveTextContent("null");
  });

  it("행 아무 데나 눌러도 수정 화면으로 간다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage();
    await findText("베토벤");
    await user.click(within(row(4)).getByText("Beethoven, Ludwig van"));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/composers/4"));
  });
});

describe("ComposerAdminListPage — 검색·필터·페이지", () => {
  it("URL 쿼리를 그대로 API 에 넘기고 검색·필터 UI 에 반영한다", async () => {
    renderPage("/admin/composers?q=베토&missingKo=true&page=1", [
      { url: LIST, data: pageResponse([adminComposer()], { page: 1, totalElements: 25 }) },
    ]);
    await findText("베토벤");
    expect(lastParams().get("q")).toBe("베토");
    expect(lastParams().get("missingKo")).toBe("true");
    expect(lastParams().get("page")).toBe("1");
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveValue("베토");
    expect(screen.getByRole("checkbox", { name: MISSING_KO })).toBeChecked();
  });

  it("쿼리가 없으면 빈 값은 보내지 않는다", async () => {
    renderPage();
    await findText("베토벤");
    expect(lastParams().has("q")).toBe(false);
    expect(lastParams().has("missingKo")).toBe(false);
    expect(lastParams().has("page")).toBe(false);
    expect(lastParams().has("size")).toBe(false);
  });

  it("검색어는 300ms 디바운스 뒤 한 번만 요청하고 URL 의 page 를 지운다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { getLocation } = renderPage("/admin/composers?page=2", [
      { url: LIST, data: pageResponse(adminComposers, { page: 2, totalElements: 60 }) },
    ]);
    await findText("베토벤");
    expect(findCalls(LIST)).toHaveLength(1);

    const input = screen.getByPlaceholderText(PLACEHOLDER);
    fireEvent.change(input, { target: { value: "쇼" } });
    fireEvent.change(input, { target: { value: "쇼팽" } });
    await vi.advanceTimersByTimeAsync(200);
    expect(findCalls(LIST)).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(200);
    await waitFor(() => expect(findCalls(LIST)).toHaveLength(2));
    expect(lastParams().get("q")).toBe("쇼팽");
    expect(getLocation().params.get("q")).toBe("쇼팽");
    expect(getLocation().params.has("page")).toBe(false);
  });

  it("'한글 표기 없는 작곡가만' 을 켜면 missingKo=true 로 다시 부른다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage();
    await findText("베토벤");
    await user.click(screen.getByRole("checkbox", { name: MISSING_KO }));
    await waitFor(() => expect(lastParams().get("missingKo")).toBe("true"));
    expect(getLocation().params.get("missingKo")).toBe("true");
  });

  it("페이지 버튼을 누르면 page 쿼리로 다시 부른다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage("/admin/composers", [
      { url: LIST, data: pageResponse(adminComposers, { totalElements: 45 }) },
    ]);
    await findText("베토벤");
    await user.click(screen.getByRole("button", { name: "2" }));
    await waitFor(() => expect(lastParams().get("page")).toBe("1"));
    expect(getLocation().params.get("page")).toBe("1");
  });
});

describe("ComposerAdminListPage — 로딩·실패·빈 상태", () => {
  it("로딩 중에는 표 행 스켈레톤 8줄", async () => {
    renderPage("/admin/composers", [{ url: LIST, data: pageResponse(adminComposers), delay: 30 }]);
    expect(document.querySelectorAll(".skeleton-row")).toHaveLength(8);
    await findText("베토벤");
    expect(document.querySelectorAll(".skeleton-row")).toHaveLength(0);
  });

  it("불러오기 실패 → '연결을 확인해 주세요' + '다시 시도' 로 재요청", async () => {
    const user = userEvent.setup();
    renderPage("/admin/composers", [{ url: LIST, reject: true }]);
    await findText("연결을 확인해 주세요");
    mockFetch([{ url: LIST, data: pageResponse(adminComposers) }]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("베토벤");
    expectNoText("연결을 확인해 주세요");
  });

  it("0명(검색 없음) → '등록된 작곡가가 없어요' + '새 작곡가'", async () => {
    renderPage("/admin/composers", [{ url: LIST, data: pageResponse([]) }]);
    await findText("등록된 작곡가가 없어요");
    expect(screen.getByRole("link", { name: /새 작곡가/ })).toHaveAttribute("href", "/admin/composers/new");
  });

  it("검색 0건 → \"'쇼팽'에 맞는 작곡가가 없어요\" + '검색 지우기' 로 q 제거", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage("/admin/composers?q=쇼팽", [{ url: LIST, data: pageResponse([]) }]);
    await findText("'쇼팽'에 맞는 작곡가가 없어요");
    expectNoText("등록된 작곡가가 없어요");
    await user.click(screen.getByRole("button", { name: "검색 지우기" }));
    await waitFor(() => expect(getLocation().params.has("q")).toBe(false));
  });
});
