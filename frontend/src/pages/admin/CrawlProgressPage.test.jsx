import { screen, within, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CrawlProgressPage } from "./CrawlProgressPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import { crawlJob, completedCrawlJob, crawlItemsRunning, crawlItemsCompleted } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 07_관리자_수집.md 화면 B — /admin/crawl/:jobId. 진행 모드(RUNNING/PAUSED/STOPPED) · 결과 모드(COMPLETED/FAILED)
// GET /api/admin/crawl/jobs/12 (10초 폴링은 RUNNING/PAUSED 일 때만)
const JOB = /\/api\/admin\/crawl\/jobs\/12$/;

function detail(job, items) {
  return { ...job, items };
}

function renderPage(job, items = crawlItemsRunning, extra = []) {
  mockFetch([
    { url: JOB, data: detail(job, items) },
    { url: /\/api\/admin\/crawl\/jobs\/active$/, data: job.status === "RUNNING" || job.status === "PAUSED" ? job : null },
    ...extra,
  ]);
  return renderWithProviders(<CrawlProgressPage />, { route: "/admin/crawl/12", path: "/admin/crawl/:jobId", auth: "admin" });
}

describe("CrawlProgressPage — 진행 모드(RUNNING)", () => {
  it("제목 '수집 진행', 상태 '진행 중', 진행 막대, 요약, 현재 처리, 자동 갱신 안내", async () => {
    renderPage(crawlJob());
    await findText("진행 중");
    expect(screen.getByRole("heading", { name: /수집 진행/ })).toBeInTheDocument();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "12");
    expect(bar).toHaveAttribute("aria-valuemax", "50");
    expectText("12 / 50");
    expectText("12 / 50 완료 · 성공 11 · 실패 1 · 남은 예상 시간 약 45분");
    expectText("지금 처리 중: Nocturne in E-flat major, Op.9 No.2 — 파일 받는 중 (2/2)");
    expectText("10초마다 자동으로 갱신돼요 · 마지막 갱신");
    expect(screen.getByRole("button", { name: /새로고침/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /중지/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /이어서 시작/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /실패한 것만 다시 시도/ })).not.toBeInTheDocument();
  });

  it("건너뜀이 있으면 요약에 '· 건너뜀 1', 예상 시간이 없으면 그 부분 생략", async () => {
    renderPage(crawlJob({ skipCount: 1, processedCount: 13, estimatedRemainingSeconds: null }));
    await findText("13 / 50 완료 · 성공 11 · 실패 1 · 건너뜀 1");
    expectNoText("남은 예상 시간");
  });

  it("단계 문구: 판본 정보 읽는 중 / 미리보기 만드는 중 / 다음 항목 준비 중", async () => {
    renderPage(crawlJob({ currentStage: "READING_METADATA", currentFileIndex: null, currentFileTotal: null }));
    await findText("지금 처리 중: Nocturne in E-flat major, Op.9 No.2 — 판본 정보 읽는 중");
  });

  it("처리 중 항목이 없으면 '다음 항목 준비 중'", async () => {
    renderPage(crawlJob({ currentItem: null, currentStage: null }));
    await findText("다음 항목 준비 중");
  });

  it("항목 표: 상태 문구(성공·실패·처리 중·대기)와 결과 문구, 성공은 '→ 곡 보기' 링크", async () => {
    renderPage(crawlJob());
    await findText("항목 (50)");
    const r1 = screen.getByTestId("crawl-item-row-1");
    expect(within(r1).getByText("성공")).toBeInTheDocument();
    expect(r1).toHaveTextContent("판본 14개, 파일 2개 받음");
    expect(within(r1).getByRole("link", { name: /곡 보기/ })).toHaveAttribute("href", "/admin/works/77");
    const r2 = screen.getByTestId("crawl-item-row-2");
    expect(within(r2).getByText("실패")).toBeInTheDocument();
    expect(r2).toHaveTextContent("IMSLP에 그 페이지가 없어요");
    expect(within(screen.getByTestId("crawl-item-row-3")).getByText("처리 중")).toBeInTheDocument();
    expect(within(screen.getByTestId("crawl-item-row-4")).getByText("대기")).toBeInTheDocument();
  });

  it("항목 필터 select: 전체 / 실패 / 성공 / 처리 중·대기 / 건너뜀", async () => {
    const user = userEvent.setup();
    renderPage(crawlJob());
    await findText("항목 (50)");
    const select = screen.getByRole("combobox");
    for (const name of ["전체", "실패", "성공", "처리 중·대기", "건너뜀"]) {
      expect(within(select).getByRole("option", { name })).toBeInTheDocument();
    }
    await user.selectOptions(select, "실패");
    expect(screen.queryByTestId("crawl-item-row-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("crawl-item-row-2")).toBeInTheDocument();
  });

  it("10초마다 다시 조회한다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderPage(crawlJob());
    await findText("진행 중");
    expect(findCalls(JOB)).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(10_000);
    await waitFor(() => expect(findCalls(JOB)).toHaveLength(2));
    await vi.advanceTimersByTimeAsync(10_000);
    await waitFor(() => expect(findCalls(JOB)).toHaveLength(3));
  });

  it("'새로고침' 은 즉시 재조회", async () => {
    renderPage(crawlJob());
    await findText("진행 중");
    fireEvent.click(screen.getByRole("button", { name: /새로고침/ }));
    await waitFor(() => expect(findCalls(JOB)).toHaveLength(2));
  });

  it("자동 갱신 실패 → 마지막 현황 유지 + '현황을 못 가져왔어요 — 새로고침'", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let n = 0;
    mockFetch([
      {
        url: JOB,
        handler: () => {
          n += 1;
          if (n === 1) return Promise.resolve({ ok: true, status: 200, json: async () => ({ success: true, data: detail(crawlJob(), crawlItemsRunning) }), clone() { return this; } });
          throw new TypeError("Failed to fetch");
        },
      },
      { url: /\/api\/admin\/crawl\/jobs\/active$/, data: crawlJob() },
    ]);
    renderWithProviders(<CrawlProgressPage />, { route: "/admin/crawl/12", path: "/admin/crawl/:jobId", auth: "admin" });
    await findText("진행 중");
    await vi.advanceTimersByTimeAsync(10_000);
    await findText("현황을 못 가져왔어요");
    expectText("12 / 50 완료");
  });

  it("'중지' → ConfirmDialog → '중지' 확정 → POST stop, 버튼 '중지하는 중…'", async () => {
    const user = userEvent.setup();
    renderPage(crawlJob(), crawlItemsRunning, [
      { url: /\/api\/admin\/crawl\/jobs\/12\/stop$/, method: "POST", data: crawlJob({ stopRequested: true }) },
    ]);
    await findText("진행 중");
    await user.click(screen.getByRole("button", { name: /^중지$/ }));
    await findText("수집을 중지할까요?");
    expectText("지금 처리 중인 곡은 끝까지 마친 뒤 멈춰요. 남은 항목은 '대기'로 남고, 나중에 이어서 시작할 수 있어요");
    expect(findCalls(/\/stop$/)).toHaveLength(0);
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "계속 진행" })).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "중지" }));
    await waitFor(() => expect(findCall(/\/api\/admin\/crawl\/jobs\/12\/stop$/).method).toBe("POST"));
    await findText("중지하는 중…");
  });

  it("중지 요청 실패 → '중지하지 못했어요 — 다시 시도'", async () => {
    const user = userEvent.setup();
    renderPage(crawlJob(), crawlItemsRunning, [{ url: /\/stop$/, method: "POST", status: 500, error: "INTERNAL_ERROR", message: "서버 오류" }]);
    await findText("진행 중");
    await user.click(screen.getByRole("button", { name: /^중지$/ }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "중지" }));
    await findText("중지하지 못했어요 — 다시 시도");
  });
});

describe("CrawlProgressPage — 일시 정지·중지됨", () => {
  it("PAUSED: 상태 '일시 정지', 안내 + '지금 이어서 시작', 중지 버튼 유지", async () => {
    const user = userEvent.setup();
    renderPage(crawlJob({ status: "PAUSED", pausedUntil: "2026-09-06T05:35:00Z" }), crawlItemsRunning, [
      { url: /\/resume$/, method: "POST", data: crawlJob() },
    ]);
    await findText("일시 정지");
    expect(document.body.textContent).toMatch(/IMSLP가 응답하지 않아요\. 10분 뒤 자동으로 다시 시도합니다 \(다음 시도 \d{2}:\d{2}\)/);
    expect(screen.getByRole("button", { name: /^중지$/ })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "지금 이어서 시작" }));
    await waitFor(() => expect(findCall(/\/api\/admin\/crawl\/jobs\/12\/resume$/).method).toBe("POST"));
  });

  it("STOPPED: 상태 '중지됨', 요약에 '대기 38', '이어서 시작' 버튼(중지 없음), 폴링 없음", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderPage(crawlJob({ status: "STOPPED", estimatedRemainingSeconds: null, currentItem: null, currentStage: null }), crawlItemsRunning, [
      { url: /\/resume$/, method: "POST", data: crawlJob() },
    ]);
    await findText("중지됨");
    expectText("12 / 50 완료 · 성공 11 · 실패 1 · 대기 38");
    expect(screen.getByRole("button", { name: /이어서 시작/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^중지$/ })).not.toBeInTheDocument();
    expectNoText("10초마다 자동으로 갱신돼요");
    await vi.advanceTimersByTimeAsync(10_000);
    expect(findCalls(JOB)).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: /이어서 시작/ }));
    await waitFor(() => expect(findCall(/\/api\/admin\/crawl\/jobs\/12\/resume$/).method).toBe("POST"));
  });

  it("다른 작업이 진행 중이면 '이어서 시작' 비활성 + 안내", async () => {
    renderPage(crawlJob({ status: "STOPPED", estimatedRemainingSeconds: null, currentItem: null, currentStage: null }), crawlItemsRunning, [
      { url: /\/api\/admin\/crawl\/jobs\/active$/, data: crawlJob({ id: 13 }) },
    ]);
    await findText("중지됨");
    expect(screen.getByRole("button", { name: /이어서 시작/ })).toBeDisabled();
    expectText("진행 중인 수집이 끝나면 시작할 수 있어요");
    expect(screen.getByRole("link", { name: "보기" })).toHaveAttribute("href", "/admin/crawl/13");
  });

  it("서비스 재시작으로 중단(stoppedByRestart) → 안내 문구", async () => {
    renderPage(crawlJob({ status: "STOPPED", stoppedByRestart: true, estimatedRemainingSeconds: null, currentItem: null, currentStage: null }));
    await findText("서비스가 재시작되어 멈췄어요. '이어서 시작'하면 대기 항목부터 계속하고, 이미 받은 파일은 다시 받지 않아요");
  });

  it("이어서 시작 실패 → '이어서 시작하지 못했어요 — 다시 시도'", async () => {
    const user = userEvent.setup();
    renderPage(crawlJob({ status: "STOPPED", estimatedRemainingSeconds: null, currentItem: null, currentStage: null }), crawlItemsRunning, [
      { url: /\/resume$/, method: "POST", status: 500, error: "INTERNAL_ERROR", message: "서버 오류" },
    ]);
    await findText("중지됨");
    await user.click(screen.getByRole("button", { name: /이어서 시작/ }));
    await findText("이어서 시작하지 못했어요 — 다시 시도");
  });
});

describe("CrawlProgressPage — 결과 모드(COMPLETED/FAILED)", () => {
  it("제목 '수집 결과', 상태 '완료', 요약·시작/종료, 보완 필요 안내, 실패 그룹이 위", async () => {
    renderPage(completedCrawlJob(), crawlItemsCompleted);
    await findText("완료");
    expect(screen.getByRole("heading", { name: /수집 결과/ })).toBeInTheDocument();
    expectText("총 50건 — 성공 46 · 실패 3 · 건너뜀 1 · 걸린 시간 1시간 3분");
    expect(document.body.textContent).toMatch(/시작 \d{2}-\d{2} \d{2}:\d{2} · 종료 \d{2}-\d{2} \d{2}:\d{2}/);
    expectNoText("10초마다 자동으로 갱신돼요");
    expect(screen.queryByRole("button", { name: /^중지$/ })).not.toBeInTheDocument();

    const alert = screen.getByText(/성공한 곡은 한국어 제목·별칭·난이도가 비어 있어 '보완 필요' 상태예요/);
    expect(alert).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /곡 관리에서 보완하기/ })).toHaveAttribute("href", "/admin/works?status=NEEDS_WORK");

    const failed = screen.getByText("실패 (3)");
    const rest = screen.getByText("성공 (46) · 건너뜀 (1)");
    expect(failed.compareDocumentPosition(rest) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("결과 열 문구: 실패 사유·숨김·건너뜀·파일 받아오기 재시도 안내", async () => {
    renderPage(completedCrawlJob(), crawlItemsCompleted);
    await findText("완료");
    expect(screen.getByTestId("crawl-item-row-2")).toHaveTextContent("IMSLP에 그 페이지가 없어요");
    const r17 = screen.getByTestId("crawl-item-row-17");
    expect(r17).toHaveTextContent("파일을 받다가 끊겼어요");
    expect(r17).toHaveTextContent("(파일 받아오기로 재시도)");
    expect(within(r17).getByRole("link", { name: /곡 보기/ })).toHaveAttribute("href", "/admin/works/78");
    const r33 = screen.getByTestId("crawl-item-row-33");
    expect(within(r33).getByText("숨김")).toBeInTheDocument();
    expect(r33).toHaveTextContent("피아노 독주곡이 아닌 것 같아요");
    expect(r33).toHaveTextContent("(확인 후 숨김 해제)");
    const r40 = screen.getByTestId("crawl-item-row-40");
    expect(within(r40).getByText("건너뜀")).toBeInTheDocument();
    expect(r40).toHaveTextContent("이미 있음 — 건너뜀");
  });

  it("'실패한 것만 다시 시도' → ConfirmDialog '실패한 3건으로 새 수집 작업을 만들까요?' → POST retry-failed → 새 작업 화면으로", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderPage(completedCrawlJob(), crawlItemsCompleted, [
      { url: /\/retry-failed$/, method: "POST", data: crawlJob({ id: 13, retryOfJobId: 12, totalCount: 3 }), status: 201 },
      { url: /\/api\/admin\/crawl\/jobs\/13$/, data: detail(crawlJob({ id: 13, retryOfJobId: 12, totalCount: 3 }), []) },
    ]);
    await findText("완료");
    await user.click(screen.getByRole("button", { name: /실패한 것만 다시 시도/ }));
    await findText("실패한 3건으로 새 수집 작업을 만들까요?");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /다시 시도|만들기|확인/ }));
    await waitFor(() => expect(findCall(/\/api\/admin\/crawl\/jobs\/12\/retry-failed$/).method).toBe("POST"));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/crawl/13"));
  });

  it("실패 0건이면 재시도 버튼·실패 그룹 없음, 성공 0건이면 보완 안내 없음", async () => {
    renderPage(completedCrawlJob({ failCount: 0, successCount: 49 }), [crawlItemsCompleted[0]]);
    await findText("완료");
    expect(screen.queryByRole("button", { name: /실패한 것만 다시 시도/ })).not.toBeInTheDocument();
    expectNoText("실패 (");
  });

  it("작업 실패(FAILED): 상태 '실패' + 사유 + 재시도 버튼", async () => {
    renderPage(completedCrawlJob({ status: "FAILED", failureReason: "IMSLP가 응답하지 않아요" }), crawlItemsCompleted);
    await findText("IMSLP가 응답하지 않아요");
    expect(screen.getAllByText("실패").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /실패한 것만 다시 시도/ })).toBeInTheDocument();
  });
});

describe("CrawlProgressPage — 오류", () => {
  it("없는 jobId(404) → '찾을 수 없는 페이지예요'", async () => {
    mockFetch([
      { url: JOB, status: 404, error: "NOT_FOUND", message: "작업이 없어요" },
      { url: /\/api\/admin\/crawl\/jobs\/active$/, data: null },
    ]);
    renderWithProviders(<CrawlProgressPage />, { route: "/admin/crawl/12", path: "/admin/crawl/:jobId", auth: "admin" });
    await findText("찾을 수 없는 페이지예요");
  });

  it("첫 진입 실패 → '연결을 확인해 주세요' + '다시 시도'", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: JOB, reject: true }, { url: /\/api\/admin\/crawl\/jobs\/active$/, data: null }]);
    renderWithProviders(<CrawlProgressPage />, { route: "/admin/crawl/12", path: "/admin/crawl/:jobId", auth: "admin" });
    await findText("연결을 확인해 주세요");
    mockFetch([{ url: JOB, data: detail(crawlJob(), crawlItemsRunning) }, { url: /\/api\/admin\/crawl\/jobs\/active$/, data: crawlJob() }]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("진행 중");
  });
});
