import { screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CrawlAdminPage } from "./CrawlAdminPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import { crawlCheckResponse, crawlJob, completedCrawlJob } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 07_관리자_수집.md 화면 A — /admin/crawl
// 진입: GET /api/admin/crawl/jobs?page=0, GET /api/admin/crawl/jobs/active
// 확인: POST /api/admin/crawl/check {urls, fetchFiles} / 시작: POST /api/admin/crawl/jobs {items:[{url, refresh}], fetchFiles}
const CHECK = /\/api\/admin\/crawl\/check$/;
const JOBS = /\/api\/admin\/crawl\/jobs(\?|$)/;
const ACTIVE = /\/api\/admin\/crawl\/jobs\/active$/;
const LABEL = "IMSLP 작품 페이지 주소를 한 줄에 하나씩 붙여넣어 주세요";
const FETCH_FILES = "파일도 함께 받기";
const NOTICE_1 = "IMSLP 서버에 부담을 주지 않도록 요청 사이에 2초 이상 쉬며 천천히 가져옵니다.";
const NOTICE_FILES = "곡당 보통 1~2분, 파일이 크면 더 걸립니다.";
const NOTICE_NO_FILES = "파일을 받지 않으므로 곡당 몇 초면 끝납니다.";
const NOTICE_3 = "화면을 닫아도 수집은 계속됩니다.";

const urls = crawlCheckResponse.items.map((i) => i.inputUrl);
const urlText = urls.join("\n");

function jobsPage(content) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true };
}

function renderPage({ jobs = [crawlJob({ id: 12 }), completedCrawlJob({ id: 11 }), crawlJob({ id: 10, status: "STOPPED", estimatedRemainingSeconds: null, currentItem: null, currentStage: null, finishedAt: "2026-09-04T09:52:00Z", elapsedSeconds: 720 })], active = null, check = crawlCheckResponse, extra = [] } = {}) {
  mockFetch([
    { url: JOBS, method: "GET", data: jobsPage(jobs) },
    { url: ACTIVE, data: active },
    { url: CHECK, method: "POST", data: check },
    { url: /\/api\/admin\/crawl\/jobs$/, method: "POST", data: crawlJob({ id: 12 }), status: 201 },
    ...extra,
  ]);
  return renderWithProviders(<CrawlAdminPage />, { route: "/admin/crawl", path: "/admin/crawl", auth: "admin" });
}

async function pasteAndCheck(user, text = urlText) {
  await user.click(screen.getByLabelText(LABEL));
  await user.paste(text);
  await user.click(screen.getByRole("button", { name: "확인" }));
  await findText("수집 예정 48건");
}

describe("CrawlAdminPage — 등록 패널", () => {
  it("제목·라벨·자리 문구·'파일도 함께 받기'(기본 켬)·고정 안내", async () => {
    renderPage();
    expect(screen.getByRole("heading", { name: /수집 관리/ })).toBeInTheDocument();
    expectText("수집 대상 등록");
    expect(screen.getByLabelText(LABEL)).toHaveAttribute("placeholder", "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)");
    expect(screen.getByRole("checkbox", { name: FETCH_FILES })).toBeChecked();
    expectText("끄면 판본 정보만 가져오고 PDF는 받지 않아요 — 나중에 판본별 '파일 받아오기'로 받을 수 있어요");
    expectText(NOTICE_1);
    expectText(NOTICE_FILES);
    expectText(NOTICE_3);
    await findText("수집 작업 목록");
  });

  it("입력이 비면 '확인'·'수집 시작' 비활성, 줄 수는 빈 줄 제외", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(screen.getByRole("button", { name: "확인" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "수집 시작" })).toBeDisabled();
    await user.click(screen.getByLabelText(LABEL));
    await user.paste("https://imslp.org/wiki/A\n\n  \nhttps://imslp.org/wiki/B\n");
    expectText("2줄");
    expect(screen.getByRole("button", { name: "확인" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "수집 시작" })).toBeDisabled();
  });

  it("'파일도 함께 받기' 를 끄면 안내 두 번째 문장이 바뀐다", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("checkbox", { name: FETCH_FILES }));
    expectText(NOTICE_NO_FILES);
    expectNoText(NOTICE_FILES);
    expectText(NOTICE_1);
  });
});

describe("CrawlAdminPage — 확인(판정)", () => {
  it("확인 → POST check {urls(빈 줄 제외), fetchFiles:true} → 판정 표 + 요약 줄, 수집 시작 활성", async () => {
    const user = userEvent.setup();
    renderPage();
    await pasteAndCheck(user);
    const body = findCall(CHECK).body;
    expect(body.urls).toEqual(urls);
    expect(body.fetchFiles).toBe(true);
    expectText("수집 예정 48건 (새 곡 30 · 등록된 곡에 붙임 18) · 건너뜀 2건 · 오류 1건 · 예상 소요 약 1시간 10분");
    expect(screen.getAllByText("수집 예정")).toHaveLength(2);
    expect(screen.getByText("이미 있음 — 건너뜀")).toBeInTheDocument();
    expect(screen.getByText("주소 형식 오류")).toBeInTheDocument();
    expect(screen.getByText("중복 제거")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "수집 시작" })).toBeEnabled();
  });

  it("ATTACH 가 0건이면 괄호를 생략한다", async () => {
    const user = userEvent.setup();
    renderPage({ check: { ...crawlCheckResponse, summary: { ...crawlCheckResponse.summary, newCount: 48, attachCount: 0 } } });
    await pasteAndCheck(user);
    expectText("수집 예정 48건 · 건너뜀 2건");
    expectNoText("(새 곡");
  });

  it("'정보만 다시 가져오기' 를 켜면 요약에 '· 정보 갱신 1건'", async () => {
    const user = userEvent.setup();
    renderPage();
    await pasteAndCheck(user);
    await user.click(screen.getByRole("checkbox", { name: "정보만 다시 가져오기" }));
    expectText("· 정보 갱신 1건");
  });

  it("'파일도 함께 받기' 를 바꾸면 '다시 확인해 주세요' + 수집 시작 비활성, 다시 확인하면 fetchFiles:false 로 요청", async () => {
    const user = userEvent.setup();
    renderPage();
    await pasteAndCheck(user);
    await user.click(screen.getByRole("checkbox", { name: FETCH_FILES }));
    expectText("다시 확인해 주세요");
    expect(screen.getByRole("button", { name: "수집 시작" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "확인" }));
    await waitFor(() => expect(findCalls(CHECK)).toHaveLength(2));
    expect(findCalls(CHECK)[1].body.fetchFiles).toBe(false);
    await waitFor(() => expect(screen.getByRole("button", { name: "수집 시작" })).toBeEnabled());
    expectNoText("다시 확인해 주세요");
  });

  it("입력을 고쳐도 판정 표가 낡은 것이므로 수집 시작 비활성 + '다시 확인해 주세요'", async () => {
    const user = userEvent.setup();
    renderPage();
    await pasteAndCheck(user);
    await user.type(screen.getByLabelText(LABEL), "\nhttps://imslp.org/wiki/C");
    expect(screen.getByRole("button", { name: "수집 시작" })).toBeDisabled();
    expectText("다시 확인해 주세요");
  });

  it("판정 결과가 전부 오류/건너뜀이면 '수집할 주소가 없어요' + 비활성", async () => {
    const user = userEvent.setup();
    renderPage({
      check: {
        items: [crawlCheckResponse.items[3], crawlCheckResponse.items[2]],
        summary: { newCount: 0, attachCount: 0, existsCount: 1, invalidCount: 1, duplicateCount: 0, estimatedSeconds: 0 },
      },
    });
    await user.click(screen.getByLabelText(LABEL));
    await user.paste("https://example.com/foo\nhttps://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Frédéric)");
    await user.click(screen.getByRole("button", { name: "확인" }));
    await findText("수집 예정 0건");
    expectText("수집할 주소가 없어요");
    expect(screen.getByRole("button", { name: "수집 시작" })).toBeDisabled();
  });

  it("확인 실패 → '주소를 확인하지 못했어요. 잠시 후 다시 시도해 주세요'", async () => {
    const user = userEvent.setup();
    renderPage({ extra: [{ url: CHECK, method: "POST", reject: true }] });
    await user.click(screen.getByLabelText(LABEL));
    await user.paste(urlText);
    await user.click(screen.getByRole("button", { name: "확인" }));
    await findText("주소를 확인하지 못했어요. 잠시 후 다시 시도해 주세요");
  });
});

describe("CrawlAdminPage — 수집 시작", () => {
  it("POST jobs {items(오류·중복 제외, refresh 반영), fetchFiles} → /admin/crawl/:jobId 로 이동", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage();
    await pasteAndCheck(user);
    await user.click(screen.getByRole("checkbox", { name: "정보만 다시 가져오기" }));
    await user.click(screen.getByRole("button", { name: "수집 시작" }));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/crawl/12"));
    const body = findCall(/\/api\/admin\/crawl\/jobs$/).body;
    expect(body.fetchFiles).toBe(true);
    expect(body.items).toEqual([
      { url: urls[0], refresh: false },
      { url: urls[1], refresh: false },
      { url: urls[2], refresh: true },
    ]);
  });

  it("시작 실패 → '수집을 시작하지 못했어요. 잠시 후 다시 시도해 주세요'", async () => {
    const user = userEvent.setup();
    renderPage({ extra: [{ url: /\/api\/admin\/crawl\/jobs$/, method: "POST", status: 500, error: "INTERNAL_ERROR", message: "서버 오류" }] });
    await pasteAndCheck(user);
    await user.click(screen.getByRole("button", { name: "수집 시작" }));
    await findText("수집을 시작하지 못했어요. 잠시 후 다시 시도해 주세요");
  });

  it("진행 중 작업이 있으면 수집 시작 비활성 + '진행 중인 수집이 끝나면 시작할 수 있어요 — 보기' (확인은 가능)", async () => {
    const user = userEvent.setup();
    renderPage({ active: crawlJob({ id: 12 }) });
    await findText("진행 중인 수집이 끝나면 시작할 수 있어요");
    expect(screen.getByRole("link", { name: "보기" })).toHaveAttribute("href", "/admin/crawl/12");
    await pasteAndCheck(user);
    expect(screen.getByRole("button", { name: "수집 시작" })).toBeDisabled();
  });

  it("409(동시 실행) → 진행 중 안내와 동일", async () => {
    const user = userEvent.setup();
    renderPage({ extra: [{ url: /\/api\/admin\/crawl\/jobs$/, method: "POST", status: 409, error: "DUPLICATE_RESOURCE", message: "진행 중인 수집이 있어요" }] });
    await pasteAndCheck(user);
    await user.click(screen.getByRole("button", { name: "수집 시작" }));
    await findText("진행 중인 수집이 끝나면 시작할 수 있어요");
  });
});

describe("CrawlAdminPage — 작업 목록", () => {
  it("최근 순 목록: 상태 문구·대상·성공·실패·건너뜀·걸린 시간·보기 링크", async () => {
    renderPage();
    await findText("수집 작업 목록");
    const running = screen.getByTestId("crawl-job-row-12");
    expect(within(running).getByText("진행 중")).toBeInTheDocument();
    expect(running).toHaveTextContent("50");
    expect(running).toHaveTextContent("11");
    expect(within(running).getByRole("link", { name: /보기/ })).toHaveAttribute("href", "/admin/crawl/12");
    const done = screen.getByTestId("crawl-job-row-11");
    expect(within(done).getByText("완료")).toBeInTheDocument();
    expect(done).toHaveTextContent("1시간 3분");
    const stopped = screen.getByTestId("crawl-job-row-10");
    expect(within(stopped).getByText("중지됨")).toBeInTheDocument();
  });

  it("작업 0건 → '아직 수집한 적이 없어요'", async () => {
    renderPage({ jobs: [] });
    await findText("아직 수집한 적이 없어요");
  });

  it("목록 실패 → 목록 자리에만 '연결을 확인해 주세요', 등록 패널은 살아 있음", async () => {
    renderPage({ extra: [{ url: JOBS, method: "GET", reject: true }] });
    await findText("연결을 확인해 주세요");
    expect(screen.getByLabelText(LABEL)).toBeInTheDocument();
  });
});
