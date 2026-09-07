import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CopyrightPendingPage } from "./CopyrightPendingPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import {
  adminComposers,
  adminEdition,
  pageResponse,
  pendingCopyrightResponse,
} from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 06_관리자_판본관리.md 화면 C — /admin/copyright
// 목록: GET /api/admin/copyright/pending?q=&composerId=&page= (02_API §5-8)
// 행 판정: PUT /api/admin/editions/{id}/copyright (§5-9) / 일괄: POST /api/admin/editions/copyright/bulk (§5-10)
// 작곡가 필터 선택지: GET /api/admin/composers?size=200 (§4-2 + size)
const PENDING = /\/api\/admin\/copyright\/pending/;
const JUDGE_305 = /\/api\/admin\/editions\/305\/copyright$/;
const BULK = /\/api\/admin\/editions\/copyright\/bulk$/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const PLACEHOLDER = "곡 이름, 작곡가";
const NOTE = "작곡가 1849 사망, 편집자 1941 사망 → 경과";

function renderPage(route = "/admin/copyright", routes = []) {
  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: PENDING, method: "GET", data: pendingCopyrightResponse() },
    { url: JUDGE_305, method: "PUT", data: { ...adminEdition({ id: 305, koreaCopyright: "FREE" }), workStatus: "READY" } },
    ...routes,
  ]);
  return renderWithProviders(<CopyrightPendingPage />, { route, path: "/admin/copyright", auth: "admin" });
}

function lastParams() {
  const calls = findCalls(PENDING);
  return calls[calls.length - 1].params;
}

function row(editionId) {
  return screen.getByTestId(`pending-row-${editionId}`);
}

async function judgeRow(user, editionId, { verdict = "자유 이용 가능", note = NOTE } = {}) {
  const target = row(editionId);
  await user.click(within(target).getByRole("button", { name: verdict }));
  await user.type(within(target).getByLabelText("판정 메모"), note);
  await user.click(within(target).getByRole("button", { name: "저장" }));
}

describe("CopyrightPendingPage — 머리말·표", () => {
  it("제목·대기 건수·브레드크럼", async () => {
    renderPage();
    await findText("Nocturnes, Op.9");
    expect(screen.getByRole("heading", { name: /저작권 판정 대기함/ })).toBeInTheDocument();
    expectText("확인 중인 판본 19개");
    expect(screen.getByRole("link", { name: "관리" })).toHaveAttribute("href", "/admin");
  });

  it("표 열 머리: 곡 / 작곡가 / 판본 / 편집자 / IMSLP 표기 / 판정", async () => {
    renderPage();
    await findText("Nocturnes, Op.9");
    for (const name of ["곡", "작곡가", "판본", "편집자", "IMSLP 표기", "판정"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
  });

  it("행: 곡 링크(새 탭)·작곡가(몰년)·판본·편집자·IMSLP 표기 + 파일 페이지 링크", async () => {
    renderPage();
    await findText("Nocturnes, Op.9");
    const first = row(305);
    const workLink = within(first).getByRole("link", { name: /Nocturnes, Op\.9/ });
    expect(workLink).toHaveAttribute("href", "/admin/works/23");
    expect(workLink).toHaveAttribute("target", "_blank");
    expect(first).toHaveTextContent("쇼팽 (1849)");
    expect(first).toHaveTextContent("전체 악보 · 전곡");
    expect(first).toHaveTextContent("Ignacy Paderewski");
    expect(first).toHaveTextContent("Public Domain");
    expect(within(first).getByRole("link", { name: /IMSLP/ })).toHaveAttribute(
      "href",
      "https://imslp.org/wiki/Special:ImagefromIndex/00014",
    );
  });

  it("몰년이 없으면 '(몰년 없음)', 편집자가 없으면 '–'", async () => {
    renderPage();
    await findText("월광 소나타");
    const second = row(306);
    expect(second).toHaveTextContent("베토벤 (몰년 없음)");
    expect(second).toHaveTextContent("–");
    expect(second).not.toHaveTextContent("null");
  });
});

describe("CopyrightPendingPage — 검색·필터·페이지", () => {
  it("URL 쿼리를 그대로 API 에 넘기고 필터 UI 에 반영한다", async () => {
    renderPage("/admin/copyright?q=녹턴&composerId=9&page=1", [
      { url: PENDING, method: "GET", data: pendingCopyrightResponse({ page: 1, totalElements: 40 }) },
    ]);
    await findText("Nocturnes, Op.9");
    expect(lastParams().get("q")).toBe("녹턴");
    expect(lastParams().get("composerId")).toBe("9");
    expect(lastParams().get("page")).toBe("1");
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveValue("녹턴");
    expect(screen.getByLabelText("작곡가")).toHaveDisplayValue("쇼팽");
  });

  it("검색어는 300ms 디바운스 뒤 한 번만 요청한다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderPage();
    await findText("Nocturnes, Op.9");
    expect(findCalls(PENDING)).toHaveLength(1);

    const input = screen.getByPlaceholderText(PLACEHOLDER);
    fireEvent.change(input, { target: { value: "녹" } });
    fireEvent.change(input, { target: { value: "녹턴" } });
    await vi.advanceTimersByTimeAsync(200);
    expect(findCalls(PENDING)).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(200);
    await waitFor(() => expect(findCalls(PENDING)).toHaveLength(2));
    expect(lastParams().get("q")).toBe("녹턴");
  });

  it("작곡가 필터를 고르면 composerId 로 다시 부른다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage();
    await findText("Nocturnes, Op.9");
    expect(findCall(COMPOSERS).params.get("size")).toBe("200");
    const select = screen.getByLabelText("작곡가");
    await user.selectOptions(select, within(select).getByRole("option", { name: "쇼팽" }));
    await waitFor(() => expect(lastParams().get("composerId")).toBe("9"));
    expect(getLocation().params.get("composerId")).toBe("9");
  });

  it("페이지 버튼을 누르면 page 쿼리로 다시 부른다", async () => {
    const user = userEvent.setup();
    renderPage("/admin/copyright", [
      { url: PENDING, method: "GET", data: pendingCopyrightResponse({ totalElements: 45 }) },
    ]);
    await findText("Nocturnes, Op.9");
    await user.click(screen.getByRole("button", { name: "2" }));
    await waitFor(() => expect(lastParams().get("page")).toBe("1"));
  });
});

describe("CopyrightPendingPage — 행 판정", () => {
  it("판정과 메모가 모두 있어야 '저장' 이 활성되고, 메모가 비면 근거를 요구한다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("Nocturnes, Op.9");
    const target = row(305);
    expect(within(target).getByRole("button", { name: "저장" })).toBeDisabled();

    await user.click(within(target).getByRole("button", { name: "자유 이용 가능" }));
    expect(within(target).getByRole("button", { name: "자유 이용 가능" })).toHaveAttribute("aria-pressed", "true");
    expect(within(target).getByRole("button", { name: "저장" })).toBeDisabled();
    expect(target).toHaveTextContent("판정 근거를 적어 주세요");

    await user.type(within(target).getByLabelText("판정 메모"), NOTE);
    expect(within(target).getByRole("button", { name: "저장" })).toBeEnabled();
  });

  it("저장하면 PUT 후 그 행이 사라지고 '판정했어요' + 건수가 하나 준다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("Nocturnes, Op.9");
    await judgeRow(user, 305);

    await waitFor(() => expect(findCalls(JUDGE_305)).toHaveLength(1));
    expect(findCalls(JUDGE_305)[0].body).toEqual({ koreaCopyright: "FREE", copyrightNote: NOTE });
    await waitFor(() => expect(screen.queryByTestId("pending-row-305")).not.toBeInTheDocument());
    await findText("판정했어요");
    expectText("확인 중인 판본 18개");
    expect(screen.getByTestId("pending-row-306")).toBeInTheDocument();
  });

  it("'이용 제한' 도 같은 방식으로 보낸다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("Nocturnes, Op.9");
    await judgeRow(user, 305, { verdict: "이용 제한" });
    await waitFor(() => expect(findCalls(JUDGE_305)).toHaveLength(1));
    expect(findCalls(JUDGE_305)[0].body.koreaCopyright).toBe("RESTRICTED");
  });

  it("행 저장 실패 → 그 행 아래 '판정을 저장하지 못했어요 — 다시 시도', 행은 남는다", async () => {
    const user = userEvent.setup();
    renderPage("/admin/copyright", [
      { url: JUDGE_305, method: "PUT", status: 500, error: "INTERNAL_ERROR", message: "서버 오류" },
    ]);
    await findText("Nocturnes, Op.9");
    await judgeRow(user, 305);
    await findText("판정을 저장하지 못했어요 — 다시 시도");
    expect(screen.getByTestId("pending-row-305")).toBeInTheDocument();
  });
});

describe("CopyrightPendingPage — 일괄 판정", () => {
  it("체크하면 일괄 바가 뜨고, 메모가 없으면 적용 버튼이 잠긴다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("Nocturnes, Op.9");
    expectNoText("선택 1개");

    await user.click(within(row(305)).getByRole("checkbox"));
    await findText("선택 1개");
    await user.click(screen.getByRole("radio", { name: "자유 이용 가능" }));
    expect(screen.getByRole("button", { name: "선택한 1개에 적용" })).toBeDisabled();
    expectText("판정 근거를 적어 주세요");
  });

  it("전체 선택 체크박스는 현재 페이지 전부를 고른다", async () => {
    const user = userEvent.setup();
    renderPage();
    await findText("Nocturnes, Op.9");
    await user.click(screen.getByRole("checkbox", { name: "전체 선택" }));
    await findText("선택 2개");
    expect(within(row(305)).getByRole("checkbox")).toBeChecked();
    expect(within(row(306)).getByRole("checkbox")).toBeChecked();
  });

  it("적용 → 확인 대화상자(메모 미리보기) → POST bulk → 행들이 사라지고 '2개를 판정했어요'", async () => {
    const user = userEvent.setup();
    renderPage("/admin/copyright", [
      { url: BULK, method: "POST", data: { succeeded: [305, 306], failed: [] } },
    ]);
    await findText("Nocturnes, Op.9");
    await user.click(screen.getByRole("checkbox", { name: "전체 선택" }));
    await user.click(screen.getByRole("radio", { name: "자유 이용 가능" }));
    await user.type(screen.getByLabelText("일괄 판정 메모"), NOTE);
    await user.click(screen.getByRole("button", { name: "선택한 2개에 적용" }));

    const dialog = screen.getByRole("dialog", { name: "2개 판본을 '자유 이용 가능'으로 판정할까요?" });
    expect(dialog).toHaveTextContent(NOTE);
    await user.click(within(dialog).getByRole("button", { name: "판정" }));

    await waitFor(() => expect(findCalls(BULK)).toHaveLength(1));
    expect(findCalls(BULK)[0].body).toEqual({ editionIds: [305, 306], koreaCopyright: "FREE", copyrightNote: NOTE });
    await waitFor(() => expect(screen.queryByTestId("pending-row-305")).not.toBeInTheDocument());
    expect(screen.queryByTestId("pending-row-306")).not.toBeInTheDocument();
    await findText("2개를 판정했어요");
  });

  it("일부 실패 → 표 위 경고와 실패한 행만 남는다", async () => {
    const user = userEvent.setup();
    renderPage("/admin/copyright", [
      { url: BULK, method: "POST", data: { succeeded: [305], failed: [{ editionId: 306, reason: "NOT_FOUND" }] } },
    ]);
    await findText("Nocturnes, Op.9");
    await user.click(screen.getByRole("checkbox", { name: "전체 선택" }));
    await user.click(screen.getByRole("radio", { name: "자유 이용 가능" }));
    await user.type(screen.getByLabelText("일괄 판정 메모"), NOTE);
    await user.click(screen.getByRole("button", { name: "선택한 2개에 적용" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "판정" }));

    await findText("1개 판정, 1개 실패 — 실패한 행은 목록에 남아 있어요");
    expect(screen.queryByTestId("pending-row-305")).not.toBeInTheDocument();
    expect(screen.getByTestId("pending-row-306")).toBeInTheDocument();
  });
});

describe("CopyrightPendingPage — 로딩·실패·빈 상태", () => {
  it("로딩 중에는 표 스켈레톤", async () => {
    renderPage("/admin/copyright", [{ url: PENDING, method: "GET", data: pendingCopyrightResponse(), delay: 30 }]);
    expect(document.querySelector(".skeleton-row")).toBeInTheDocument();
    await findText("Nocturnes, Op.9");
  });

  it("불러오기 실패 → '연결을 확인해 주세요' + '다시 시도'", async () => {
    const user = userEvent.setup();
    renderPage("/admin/copyright", [{ url: PENDING, method: "GET", reject: true }]);
    await findText("연결을 확인해 주세요");
    mockFetch([
      { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
      { url: PENDING, method: "GET", data: pendingCopyrightResponse() },
    ]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("Nocturnes, Op.9");
    expectNoText("연결을 확인해 주세요");
  });

  it("대기 0개 → '확인 중인 판본이 없어요' / '모든 판본의 판정이 끝났어요'", async () => {
    renderPage("/admin/copyright", [
      { url: PENDING, method: "GET", data: pendingCopyrightResponse({ editions: [], unfilteredTotal: 0 }) },
    ]);
    await findText("확인 중인 판본이 없어요");
    expectText("모든 판본의 판정이 끝났어요");
  });

  it("검색·필터 0건 → '이 조건에 맞는 판본이 없어요' + '필터 해제'", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage("/admin/copyright?q=없는곡&composerId=9", [
      { url: PENDING, method: "GET", data: pendingCopyrightResponse({ editions: [], totalElements: 0 }) },
    ]);
    await findText("이 조건에 맞는 판본이 없어요");
    expectNoText("확인 중인 판본이 없어요");
    await user.click(screen.getByRole("button", { name: "필터 해제" }));
    await waitFor(() => expect(getLocation().search).toBe(""));
  });
});
