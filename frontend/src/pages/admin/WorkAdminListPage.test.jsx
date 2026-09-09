import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WorkAdminListPage } from "./WorkAdminListPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import {
  adminComposers,
  adminWorkHidden,
  adminWorkNeedsWork,
  adminWorksResponse,
  adminWorkSummary,
  pageResponse,
} from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 05_관리자_홈_및_작곡가곡관리.md 화면 D — /admin/works
// 목록: GET /api/admin/works?q=&status=&composerId=&level=&page= (02_API §4-6)
// 작곡가 필터 선택지: GET /api/admin/composers?size=200 (§4-2 + size — senior-dev 결정, 아래 주석 참고)
//   ─ §4-2 는 20개 고정 페이지라 필터 select 를 채울 수 없다. size(기본 20, 최대 200)를 명세에 추가했다.
// URL 쿼리가 상태의 원본이고, 검색·필터를 바꾸면 page 쿼리를 지운다(lib/workQuery.js 관례).
const WORKS = /\/api\/admin\/works(\?|$)/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const PLACEHOLDER = "곡 이름, 작곡가, 작품번호";

function renderPage(route = "/admin/works", routes) {
  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: WORKS, method: "GET", data: adminWorksResponse() },
    ...(routes ?? []),
  ]);
  return renderWithProviders(<WorkAdminListPage />, { route, path: "/admin/works", auth: "admin" });
}

function lastParams() {
  const calls = findCalls(WORKS);
  return calls[calls.length - 1].params;
}

function row(id) {
  return screen.getByTestId(`admin-work-row-${id}`);
}

function pickOption(labelText, optionName) {
  const select = screen.getByLabelText(labelText);
  return { select, option: within(select).getByRole("option", { name: optionName }) };
}

/**
 * 필터 select 의 선택지를 [value, 문구] 목록으로 뽑는다.
 *
 * <p>선택지는 <b>개수가 아니라 목록으로</b> 단언한다(2026-09-08, senior-dev). 개수만 세면 항목 하나가
 * 바뀌어도 수가 같으면 안 걸리는데, 실제로 그렇게 놓쳤다 — §4-6 에 `NEEDS_RECOMMENDATION_REVIEW` 가
 * 들어와 상태가 7종이 됐는데 "7개" 단언은 그대로 통과했고, 곡 목록 필터에서 그 항목만 조용히 빠졌다.
 *
 * <p>문구뿐 아니라 <b>value 까지</b> 단언한다. 관리자가 보는 것은 문구지만 서버로 나가는 것(=계약)은 value 다.
 */
function optionPairs(labelText) {
  return within(screen.getByLabelText(labelText))
    .getAllByRole("option")
    .map((option) => [option.value, option.textContent]);
}

describe("WorkAdminListPage — 머리말·표", () => {
  it("브레드크럼 '관리 › 곡 관리', 제목, '새 곡' 버튼", async () => {
    renderPage();
    await findText("월광 소나타");
    expect(screen.getByRole("heading", { name: /곡 관리/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "관리" })).toHaveAttribute("href", "/admin");
    expect(screen.getByRole("link", { name: /새 곡/ })).toHaveAttribute("href", "/admin/works/new");
  });

  it("표 열 머리: 제목 / 작곡가 / 작품번호 / 난이도 / 판본 / 추천 / 상태 / 수정일", async () => {
    renderPage();
    await findText("월광 소나타");
    for (const name of ["제목", "작곡가", "작품번호", "난이도", "판본", "추천", "상태", "수정일"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
  });

  it("행: 제목·작곡가·작품번호·난이도·판본 수·추천 표시·상태·수정일, 행을 누르면 편집 화면", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage();
    await findText("월광 소나타");
    const moonlight = row(21);
    expect(moonlight).toHaveTextContent("월광 소나타");
    expect(moonlight).toHaveTextContent("베토벤");
    expect(moonlight).toHaveTextContent("Op.27 No.2");
    expect(moonlight).toHaveTextContent("중급");
    expect(moonlight).toHaveTextContent("14");
    expect(moonlight).toHaveTextContent("바로 받기 가능");
    expect(moonlight).toHaveTextContent("09-05");

    await user.click(within(moonlight).getByText("월광 소나타"));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/works/21"));
  });

  it("한국어 제목이 없으면 원어 제목을 보이고, 추천이 없으면 '–', 보완 필요 뱃지를 단다", async () => {
    renderPage();
    await findText("Nocturne in E-flat major, Op.9 No.2");
    const nocturne = row(23);
    expect(nocturne).toHaveTextContent("Nocturne in E-flat major, Op.9 No.2");
    expect(nocturne).toHaveTextContent("미정");
    expect(nocturne).toHaveTextContent("–");
    expect(nocturne).toHaveTextContent("보완 필요");
    expect(nocturne).not.toHaveTextContent("null");
  });

  it("숨김 + 보완 필요가 겹치면 '숨김' 을 앞에 둔다", async () => {
    renderPage("/admin/works", [
      { url: WORKS, method: "GET", data: adminWorksResponse({ works: [adminWorkHidden] }) },
    ]);
    await findText("교향곡 5번");
    const hidden = row(24);
    const hiddenBadge = within(hidden).getByText("숨김");
    const needsWork = within(hidden).getByText("보완 필요");
    expect(hiddenBadge.compareDocumentPosition(needsWork) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("수정일이 올해가 아니면 연도까지 보인다", async () => {
    renderPage("/admin/works", [
      {
        url: WORKS,
        method: "GET",
        data: adminWorksResponse({ works: [adminWorkSummary({ updatedAt: "2019-03-02T04:00:00Z" })] }),
      },
    ]);
    await findText("월광 소나타");
    expect(row(21)).toHaveTextContent("2019-03-02");
  });

  it("건수: 필터가 없으면 '312곡', 필터가 걸리면 '312곡 중 57곡'", async () => {
    renderPage();
    await findText("월광 소나타");
    expectText("312곡");
    expectNoText("곡 중");

    mockFetch([
      { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
      { url: WORKS, method: "GET", data: adminWorksResponse({ works: [adminWorkNeedsWork], totalElements: 57 }) },
    ]);
    renderWithProviders(<WorkAdminListPage />, {
      route: "/admin/works?status=NEEDS_WORK",
      path: "/admin/works",
      auth: "admin",
    });
    await findText("312곡 중 57곡");
  });
});

describe("WorkAdminListPage — 검색·필터·페이지", () => {
  it("URL 쿼리를 그대로 API 에 넘기고 필터 UI 에 반영한다", async () => {
    renderPage("/admin/works?q=녹턴&status=NEEDS_WORK&composerId=9&level=NONE&page=1", [
      { url: WORKS, method: "GET", data: adminWorksResponse({ works: [adminWorkNeedsWork], page: 1, totalElements: 57 }) },
    ]);
    await findText("Nocturne in E-flat major, Op.9 No.2");
    expect(lastParams().get("q")).toBe("녹턴");
    expect(lastParams().get("status")).toBe("NEEDS_WORK");
    expect(lastParams().get("composerId")).toBe("9");
    expect(lastParams().get("level")).toBe("NONE");
    expect(lastParams().get("page")).toBe("1");

    expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveValue("녹턴");
    expect(screen.getByLabelText("상태")).toHaveDisplayValue("보완 필요");
    expect(screen.getByLabelText("작곡가")).toHaveDisplayValue("쇼팽");
    expect(screen.getByLabelText("난이도")).toHaveDisplayValue("미정");
  });

  it("쿼리가 없으면 빈 값은 보내지 않는다", async () => {
    renderPage();
    await findText("월광 소나타");
    for (const key of ["q", "status", "composerId", "level", "page", "size"]) {
      expect(lastParams().has(key)).toBe(false);
    }
  });

  // §4-6 status = READY | PREPARING | RESTRICTED | UNKNOWN | NEEDS_WORK | NEEDS_RECOMMENDATION_REVIEW | HIDDEN
  // NEEDS_RECOMMENDATION_REVIEW 문구는 관리 홈 카드(§4-1 "추천 판본 확인 필요")와 같은 말을 쓴다 —
  // 그 카드가 이 목록으로 들어오는 입구라, 다른 말을 쓰면 관리자는 자기가 누른 필터가 걸렸는지 알 수 없다.
  it("상태 select 선택지 = '전체' + §4-6 status 7종 (값·문구·순서)", async () => {
    renderPage();
    await findText("월광 소나타");
    expect(optionPairs("상태")).toEqual([
      ["", "전체"],
      ["READY", "바로 받기 가능"],
      ["PREPARING", "준비 중"],
      ["RESTRICTED", "이용 제한"],
      ["UNKNOWN", "저작권 확인 중"],
      ["NEEDS_WORK", "보완 필요"],
      ["NEEDS_RECOMMENDATION_REVIEW", "추천 판본 확인 필요"],
      ["HIDDEN", "숨김"],
    ]);
  });

  it("난이도 select 선택지 = '전체' + §4-6 level 5종 (값·문구·순서)", async () => {
    renderPage();
    await findText("월광 소나타");
    expect(optionPairs("난이도")).toEqual([
      ["", "전체"],
      ["BEGINNER", "입문"],
      ["ELEMENTARY", "초급"],
      ["INTERMEDIATE", "중급"],
      ["ADVANCED", "고급"],
      ["NONE", "미정"],
    ]);
  });

  // 관리 홈 카드 → /admin/works?status=NEEDS_RECOMMENDATION_REVIEW (§4-1 · §4-6, 같은 모집단).
  // 선택지가 없으면 서버 필터는 걸리는데 select 만 "전체" 로 보인다 — 관리자는 목록이 왜 짧은지 알 수 없다.
  it("관리 홈 '추천 판본 확인 필요' 카드로 들어오면 select 도 그 필터를 보여준다", async () => {
    renderPage("/admin/works?status=NEEDS_RECOMMENDATION_REVIEW");
    await findText("월광 소나타");
    expect(lastParams().get("status")).toBe("NEEDS_RECOMMENDATION_REVIEW");
    expect(screen.getByLabelText("상태")).toHaveDisplayValue("추천 판본 확인 필요");
  });

  it("작곡가 select 는 작곡가 목록으로 채우고 한글 표기가 없으면 원어를 쓴다", async () => {
    renderPage();
    await findText("월광 소나타");
    expect(findCall(COMPOSERS).params.get("size")).toBe("200");
    const select = screen.getByLabelText("작곡가");
    for (const name of ["전체", "베토벤", "쇼팽", "Satie, Erik"]) {
      expect(within(select).getByRole("option", { name })).toBeInTheDocument();
    }
  });

  it("상태 필터를 바꾸면 status 쿼리로 다시 부르고 page 를 지운다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage("/admin/works?page=2", [
      { url: WORKS, method: "GET", data: adminWorksResponse({ page: 2, totalElements: 60 }) },
    ]);
    await findText("월광 소나타");
    const { select, option } = pickOption("상태", "보완 필요");
    await user.selectOptions(select, option);
    await waitFor(() => expect(lastParams().get("status")).toBe("NEEDS_WORK"));
    expect(getLocation().params.get("status")).toBe("NEEDS_WORK");
    expect(getLocation().params.has("page")).toBe(false);
  });

  it("작곡가·난이도 필터도 같은 방식으로 URL 과 요청에 반영된다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage();
    await findText("월광 소나타");

    const composer = pickOption("작곡가", "쇼팽");
    await user.selectOptions(composer.select, composer.option);
    await waitFor(() => expect(lastParams().get("composerId")).toBe("9"));

    const level = pickOption("난이도", "미정");
    await user.selectOptions(level.select, level.option);
    await waitFor(() => expect(lastParams().get("level")).toBe("NONE"));
    expect(getLocation().params.get("composerId")).toBe("9");
    expect(getLocation().params.get("level")).toBe("NONE");
  });

  it("검색어는 300ms 디바운스 뒤 한 번만 요청한다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderPage();
    await findText("월광 소나타");
    expect(findCalls(WORKS)).toHaveLength(1);

    const input = screen.getByPlaceholderText(PLACEHOLDER);
    fireEvent.change(input, { target: { value: "월" } });
    fireEvent.change(input, { target: { value: "월광" } });
    await vi.advanceTimersByTimeAsync(200);
    expect(findCalls(WORKS)).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(200);
    await waitFor(() => expect(findCalls(WORKS)).toHaveLength(2));
    expect(lastParams().get("q")).toBe("월광");
  });

  it("페이지 버튼을 누르면 page 쿼리로 다시 부른다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage("/admin/works", [
      { url: WORKS, method: "GET", data: adminWorksResponse({ totalElements: 45 }) },
    ]);
    await findText("월광 소나타");
    await user.click(screen.getByRole("button", { name: "2" }));
    await waitFor(() => expect(lastParams().get("page")).toBe("1"));
    expect(getLocation().params.get("page")).toBe("1");
  });
});

describe("WorkAdminListPage — 로딩·실패·빈 상태", () => {
  it("로딩 중에는 표 행 스켈레톤 8줄", async () => {
    renderPage("/admin/works", [{ url: WORKS, method: "GET", data: adminWorksResponse(), delay: 30 }]);
    expect(document.querySelectorAll(".skeleton-row")).toHaveLength(8);
    await findText("월광 소나타");
    expect(document.querySelectorAll(".skeleton-row")).toHaveLength(0);
  });

  it("불러오기 실패 → '연결을 확인해 주세요' + '다시 시도' 로 재요청", async () => {
    const user = userEvent.setup();
    renderPage("/admin/works", [{ url: WORKS, method: "GET", reject: true }]);
    await findText("연결을 확인해 주세요");
    mockFetch([
      { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
      { url: WORKS, method: "GET", data: adminWorksResponse() },
    ]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("월광 소나타");
    expectNoText("연결을 확인해 주세요");
  });

  it("0곡(필터 없음) → '등록된 곡이 없어요' + '새 곡' / '수집 관리로 가기'", async () => {
    renderPage("/admin/works", [
      { url: WORKS, method: "GET", data: adminWorksResponse({ works: [], unfilteredTotal: 0 }) },
    ]);
    await findText("등록된 곡이 없어요");
    expect(screen.getByRole("link", { name: /새 곡/ })).toHaveAttribute("href", "/admin/works/new");
    expect(screen.getByRole("link", { name: "수집 관리로 가기" })).toHaveAttribute("href", "/admin/crawl");
  });

  it("필터 0건 → '이 조건에 맞는 곡이 없어요' + '필터 해제' 로 쿼리를 모두 지운다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage("/admin/works?q=녹턴&status=HIDDEN&level=ADVANCED", [
      { url: WORKS, method: "GET", data: adminWorksResponse({ works: [], totalElements: 0 }) },
    ]);
    await findText("이 조건에 맞는 곡이 없어요");
    expectNoText("등록된 곡이 없어요");
    await user.click(screen.getByRole("button", { name: "필터 해제" }));
    await waitFor(() => expect(getLocation().search).toBe(""));
  });
});
