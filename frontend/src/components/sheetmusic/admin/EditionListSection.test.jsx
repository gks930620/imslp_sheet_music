import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditionListSection } from "./EditionListSection.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../../test/apiMock.js";
import { adminEdition, adminEditionCandidate, adminEditionNoFile, adminEditionRecommended, crawlJob } from "../../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../../test/text.js";

// 06_관리자_판본관리.md 화면 A — 곡 편집 화면(/admin/works/:id) 아래 판본 목록.
// props: workId, editions(AdminEditionDTO[] — 서버 정렬 그대로), recommendedEditionId, candidateEditionId,
//        composer({id, nameKo, nameOriginal, deathYear}), onChanged(), onToast(message)
// 02_API §5-5(삭제) / §5-7(파일 받아오기, 202 + 3초 폴링) / §5-2·5-3(모달 저장)
// **§5-6(추천 지정)은 2026-09-21부터 이 파일이 아니다** — 지정이 A-3 패널을 거치게 되어
// EditionListSection.recommendPanel.test.jsx(패널이 열리는 것)와 RecommendChangePanel.test.jsx(패널 안)로 옮겼다.
//
// 추천·후보 표시의 단일 기준은 **props(recommendedEditionId/candidateEditionId)** 다 (2026-09-07 senior-dev, 02 §4-7 주석).
// 판본 배열의 isRecommended/isCandidate 는 같은 사실의 파생값이라 화면에서는 읽지 않는다 —
// 추천은 "곡의 속성"이고(§5-6 응답도 곡 기준으로 돌아온다), 한 곡에 추천 하나라는 불변조건이 id 비교에서 구조적으로 지켜진다.
// 그래서 아래 픽스처가 isRecommended:true 여도 props 가 null 이면 추천 표시는 없어야 한다(의도된 어긋남).
const DELETE_302 = /\/api\/admin\/editions\/302$/;
const FETCH_FILE = /\/api\/admin\/editions\/304\/fetch-file$/;
const EDITION_304 = /\/api\/admin\/editions\/304$/;
const ACTIVE_JOB = /\/api\/admin\/crawl\/jobs\/active$/;

const composer = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827 };
const threeEditions = [adminEditionRecommended, adminEditionCandidate, adminEditionNoFile];

function renderSection({ editions = threeEditions, recommendedEditionId = 301, candidateEditionId = null, routes = [] } = {}) {
  const onChanged = vi.fn();
  const onToast = vi.fn();
  mockFetch(routes);
  const utils = renderWithProviders(
    <EditionListSection
      workId={21}
      editions={editions}
      recommendedEditionId={recommendedEditionId}
      candidateEditionId={candidateEditionId}
      composer={composer}
      onChanged={onChanged}
      onToast={onToast}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
  return { ...utils, onChanged, onToast };
}

function row(id) {
  return screen.getByTestId(`admin-edition-row-${id}`);
}

describe("EditionListSection — 목록·행 표시", () => {
  it("섹션 제목 '판본 (3개)' 과 '판본 추가' 버튼 → 판본 추가 모달", async () => {
    const user = userEvent.setup();
    renderSection({ candidateEditionId: 302 });
    expectText("판본 (3개)");
    await user.click(screen.getByRole("button", { name: /판본 추가/ }));
    expect(screen.getByRole("heading", { name: "판본 추가" })).toBeInTheDocument();
  });

  it("추천 행에는 '추천', 후보 행에는 '추천 후보' 표식", () => {
    renderSection({ candidateEditionId: 302 });
    expect(within(row(301)).getByText("추천")).toBeInTheDocument();
    expect(within(row(302)).getByText("추천 후보")).toBeInTheDocument();
    expect(within(row(304)).queryByText("추천")).not.toBeInTheDocument();
  });

  it("추천이 지정돼 있으면 후보 표식을 그리지 않는다", () => {
    renderSection({ recommendedEditionId: 301, candidateEditionId: null });
    expect(within(row(301)).getByText("추천")).toBeInTheDocument();
    expectNoText("추천 후보");
  });

  it("행 정보: 종류·범위 / 쪽수·크기 / 출판사·편집자·연도 / 저작권 뱃지 / 다운로드 수", () => {
    renderSection({ candidateEditionId: 302 });
    const recommended = row(301);
    expect(recommended).toHaveTextContent("전체 악보 · 전곡");
    expect(recommended).toHaveTextContent("12쪽 · 2.4MB");
    expect(recommended).toHaveTextContent("Breitkopf & Härtel");
    expect(recommended).toHaveTextContent("Sigmund Lebert");
    expect(recommended).toHaveTextContent("1862");
    expect(within(recommended).getByText("한국에서 자유 이용 가능")).toBeInTheDocument();
    expect(recommended).toHaveTextContent("다운로드 312");

    const candidate = row(302);
    expect(candidate).toHaveTextContent("11쪽 · 3.1MB");
    expect(candidate).toHaveTextContent("Peters");
    expect(within(candidate).getByText("저작권 확인 중")).toBeInTheDocument();
    expect(candidate).toHaveTextContent("IMSLP 다운로드 1,204");
  });

  it("파일이 없는 행: '파일 없음', 편집자 자리에 '–', 'null' 이 새지 않는다", () => {
    renderSection();
    const noFile = row(304);
    expect(noFile).toHaveTextContent("전체 악보 · 2악장만");
    expect(noFile).toHaveTextContent("파일 없음");
    expect(noFile).toHaveTextContent("–");
    expect(noFile).not.toHaveTextContent("null");
  });

  it("받은 순서 그대로 그린다 (정렬은 서버 몫)", () => {
    renderSection();
    const rows = screen.getAllByTestId(/^admin-edition-row-/);
    expect(rows.map((element) => element.dataset.testid ?? element.getAttribute("data-testid"))).toEqual([
      "admin-edition-row-301",
      "admin-edition-row-302",
      "admin-edition-row-304",
    ]);
  });

  it("행의 '수정' 은 판본 수정 모달을 연다", async () => {
    const user = userEvent.setup();
    renderSection();
    await user.click(within(row(302)).getByRole("button", { name: "수정" }));
    expect(screen.getByRole("heading", { name: "판본 수정" })).toBeInTheDocument();
  });
});

/**
 * 추천 지정 — **2026-09-21 계약 개정으로 이 자리가 얇아졌다** (기획 06 §3-1, 화면정의 06 A-2·A-3).
 *
 * <p>누르면 즉시 {@code PUT} 하던 흐름이 사라지고, 그 행 아래 A-3 패널이 열린다. 그래서 여기 있던
 * "지정 → Toast / 지정 → 행 아래 경고 / 지정 실패" 세 가지는 각각 다음으로 옮겼다:
 * <ul>
 *   <li>패널이 열리는 것 · Toast · 바꾼 뒤 행에 경고를 남기지 않는 것 → {@code EditionListSection.recommendPanel.test.jsx}</li>
 *   <li>바꾸기 전 경고 · 사유 · 메모 · 확인 체크 · 실패 처리 → {@code RecommendChangePanel.test.jsx}</li>
 * </ul>
 * 이 자리에 남는 것은 <b>패널을 열 수조차 없는 행</b>의 규칙 하나뿐이다.
 */
describe("EditionListSection — 추천 지정", () => {
  it("파일이 없는 행의 추천 지정 버튼은 비활성 + 이유를 적는다", () => {
    renderSection();
    expect(within(row(304)).getByRole("button", { name: "추천으로 지정" })).toBeDisabled();
    expect(row(304)).toHaveTextContent("파일이 없어 추천으로 지정할 수 없어요");
  });
});

describe("EditionListSection — 삭제", () => {
  it("확인 대화상자에서 삭제하면 DELETE 후 onChanged (브라우저 confirm 안 씀)", async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);
    const { onChanged, onToast } = renderSection({
      editions: [adminEditionCandidate],
      recommendedEditionId: 301,
      routes: [{ url: DELETE_302, method: "DELETE", status: 204, raw: "" }],
    });
    await user.click(within(row(302)).getByRole("button", { name: "삭제" }));
    const dialog = screen.getByRole("dialog", { name: "이 판본을 삭제할까요?" });
    expect(confirmSpy).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole("button", { name: "삭제" }));
    await waitFor(() => expect(findCalls(DELETE_302)).toHaveLength(1));
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    expect(onToast).toHaveBeenCalledWith("삭제했어요");
    confirmSpy.mockRestore();
  });

  it("추천 판본을 지우려 하면 '추천이 해제됩니다. 삭제할까요?'", async () => {
    const user = userEvent.setup();
    renderSection({ editions: [adminEditionRecommended], recommendedEditionId: 301 });
    await user.click(within(row(301)).getByRole("button", { name: "삭제" }));
    expect(screen.getByRole("dialog", { name: "추천이 해제됩니다. 삭제할까요?" })).toBeInTheDocument();
  });

  it("다운로드 기록이 있으면 확인 본문에 건수를 덧붙인다", async () => {
    const user = userEvent.setup();
    renderSection({ editions: [adminEdition({ id: 302, isRecommended: false, downloadCount: 7 })], recommendedEditionId: 301 });
    await user.click(within(row(302)).getByRole("button", { name: "삭제" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("다운로드 기록 7건이 있는 판본이에요");
  });
});

describe("EditionListSection — 빈·경고 상태", () => {
  it("판본 0개 → '아직 판본이 없어요' + 'PDF를 올리거나 수집으로 가져와요' + 판본 추가", () => {
    renderSection({ editions: [], recommendedEditionId: null });
    expectText("아직 판본이 없어요");
    expectText("PDF를 올리거나 수집으로 가져와요");
    expect(screen.getAllByRole("button", { name: /판본 추가/ }).length).toBeGreaterThan(0);
  });

  it("판본은 있는데 추천이 없으면 경고", () => {
    renderSection({ recommendedEditionId: null });
    expectText("추천 판본이 없어요 — 사용자에게는 '준비 중'으로 보여요");
  });

  it("추천이 있는데 판정이 확정되지 않았으면 경고 + '판정하기' 가 그 행 수정 모달을 연다", async () => {
    const user = userEvent.setup();
    renderSection({ editions: [adminEditionCandidate], recommendedEditionId: 302 });
    expectText("추천 판본의 저작권이 확정되지 않아 다운로드가 열리지 않아요");
    await user.click(screen.getByRole("button", { name: "판정하기" }));
    expect(screen.getByRole("heading", { name: "판본 수정" })).toBeInTheDocument();
  });

  it("추천이 FREE 면 경고가 없다", () => {
    renderSection({ editions: [adminEditionRecommended], recommendedEditionId: 301 });
    expect(within(row(301)).getByText("한국에서 자유 이용 가능")).toBeInTheDocument();
    expectNoText("추천 판본의 저작권이 확정되지 않아");
    expectNoText("추천 판본이 없어요");
  });
});

describe("EditionListSection — 파일 받아오기", () => {
  it("IMSLP 파일 정보가 있는 파일 없는 행에만 '파일 받아오기' 가 있다", () => {
    renderSection();
    expect(within(row(304)).getByRole("button", { name: /파일 받아오기/ })).toBeInTheDocument();
    expect(within(row(301)).queryByRole("button", { name: /파일 받아오기/ })).not.toBeInTheDocument();
  });

  it("202 → 버튼이 '받아오는 중…' 으로 잠기고 안내 줄이 뜬다", async () => {
    const user = userEvent.setup();
    renderSection({
      routes: [{ url: FETCH_FILE, method: "POST", data: { editionId: 304, fileFetchStatus: "QUEUED" }, status: 202 }],
    });
    await user.click(within(row(304)).getByRole("button", { name: /파일 받아오기/ }));
    await findText("IMSLP에서 받아오는 중이에요 — 2초 간격으로 천천히 받아요");
    expect(within(row(304)).getByRole("button", { name: /받아오는 중…/ })).toBeDisabled();
  });

  it("3초 폴링이 FAILED 를 받으면 '파일을 받다가 끊겼어요 — 다시 시도'", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderSection({
      routes: [
        { url: FETCH_FILE, method: "POST", data: { editionId: 304, fileFetchStatus: "QUEUED" }, status: 202 },
        {
          url: EDITION_304,
          method: "GET",
          data: { ...adminEditionNoFile, fileFetchStatus: "FAILED", fileFetchError: "연결이 끊겼어요", workId: 21, workStatus: "PREPARING" },
        },
      ],
    });
    const button = within(row(304)).getByRole("button", { name: /파일 받아오기/ });
    button.click();
    await waitFor(() => expect(findCalls(FETCH_FILE)).toHaveLength(1));
    await vi.advanceTimersByTimeAsync(3_000);
    await waitFor(() => expect(findCalls(EDITION_304)).toHaveLength(1));
    await findText("파일을 받다가 끊겼어요 — 다시 시도");
  });
});

describe("EditionListSection — 받아오는 중 수집 작업 안내 (06-A)", () => {
  // 계약: 받아오는 중인 행이 생기면 GET /api/admin/crawl/jobs/active 로 진행 중 작업을 한 번 확인한다(02 §6-5).
  // 진행 중(RUNNING/PAUSED)이면 상태 줄을 "수집이 끝난 뒤 받아와요" 로 바꾸고 진행 화면 링크를 준다.
  it("수집이 진행 중이면 '수집이 끝난 뒤 받아와요' + '진행 중인 수집 보기' 링크", async () => {
    const user = userEvent.setup();
    renderSection({
      routes: [
        { url: FETCH_FILE, method: "POST", data: { editionId: 304, fileFetchStatus: "QUEUED" }, status: 202 },
        { url: ACTIVE_JOB, method: "GET", data: crawlJob({ id: 12, status: "RUNNING" }) },
      ],
    });
    await user.click(within(row(304)).getByRole("button", { name: /파일 받아오기/ }));
    await findText("수집이 끝난 뒤 받아와요");
    expect(screen.getByRole("link", { name: "진행 중인 수집 보기" })).toHaveAttribute("href", "/admin/crawl/12");
    expectNoText("IMSLP에서 받아오는 중이에요 — 2초 간격으로 천천히 받아요");
  });

  it("진행 중인 수집이 없으면 기본 안내 줄 그대로", async () => {
    const user = userEvent.setup();
    renderSection({
      routes: [
        { url: FETCH_FILE, method: "POST", data: { editionId: 304, fileFetchStatus: "QUEUED" }, status: 202 },
        { url: ACTIVE_JOB, method: "GET", data: null },
      ],
    });
    await user.click(within(row(304)).getByRole("button", { name: /파일 받아오기/ }));
    await findText("IMSLP에서 받아오는 중이에요 — 2초 간격으로 천천히 받아요");
    expectNoText("수집이 끝난 뒤 받아와요");
  });
});
