import { screen } from "@testing-library/react";
import { AdminDashboardPage } from "./AdminDashboardPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { dashboard, crawlJob, completedCrawlJob } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 05_관리자_홈_및_작곡가곡관리.md 화면 A — GET /api/admin/dashboard

function renderDashboard(data = dashboard(), { reject = false } = {}) {
  mockFetch([
    reject ? { url: "/api/admin/dashboard", reject: true } : { url: "/api/admin/dashboard", data },
    { url: "/api/admin/crawl/jobs/active", data: null },
  ]);
  return renderWithProviders(<AdminDashboardPage />, { route: "/admin", path: "/admin", auth: "admin" });
}

function statCard(label) {
  return screen.getByText(label).closest("a, div");
}

describe("AdminDashboardPage — 숫자 카드", () => {
  it("6개 라벨과 값(천 단위 쉼표)", async () => {
    renderDashboard();
    await findText("전체 곡");
    expect(statCard("전체 곡")).toHaveTextContent("312");
    expect(statCard("바로 받기 가능")).toHaveTextContent("241");
    expect(statCard("준비 중")).toHaveTextContent("38");
    expect(statCard("보완 필요")).toHaveTextContent("57");
    expect(statCard("저작권 확인 중 판본")).toHaveTextContent("19");
    expect(statCard("이번 달 다운로드")).toHaveTextContent("1,204");
    expect(screen.getByRole("heading", { name: /관리/ })).toBeInTheDocument();
  });

  it("카드는 해당 필터가 걸린 목록으로 이어진다 (다운로드는 링크 없음)", async () => {
    renderDashboard();
    await findText("전체 곡");
    expect(screen.getByRole("link", { name: /^전체 곡/ })).toHaveAttribute("href", "/admin/works");
    expect(screen.getByRole("link", { name: /^바로 받기 가능/ })).toHaveAttribute("href", "/admin/works?status=READY");
    expect(screen.getByRole("link", { name: /^준비 중/ })).toHaveAttribute("href", "/admin/works?status=PREPARING");
    expect(screen.getByRole("link", { name: /^보완 필요/ })).toHaveAttribute("href", "/admin/works?status=NEEDS_WORK");
    expect(screen.getByRole("link", { name: /^저작권 확인 중 판본/ })).toHaveAttribute("href", "/admin/copyright");
    expect(screen.queryByRole("link", { name: /^이번 달 다운로드/ })).not.toBeInTheDocument();
  });

  it("곡 0개 → 카드 전부 0 + 첫 곡 안내", async () => {
    renderDashboard(dashboard({ totalWorks: 0, readyWorks: 0, preparingWorks: 0, needsWorkWorks: 0, unknownCopyrightEditions: 0, monthlyDownloads: 0 }));
    await findText("첫 곡을 등록해 보세요 — 수집 관리에서 IMSLP 주소를 넣거나, 곡 관리에서 직접 등록할 수 있어요");
  });
});

describe("AdminDashboardPage — 바로가기·최근 수집", () => {
  it("바로가기 4개 링크, 대기 수 뱃지", async () => {
    renderDashboard();
    await findText("전체 곡");
    expect(screen.getByRole("link", { name: /^작곡가 관리/ })).toHaveAttribute("href", "/admin/composers");
    expect(screen.getByRole("link", { name: /^곡 관리/ })).toHaveAttribute("href", "/admin/works");
    const pending = screen.getByRole("link", { name: /저작권 판정 대기함/ });
    expect(pending).toHaveAttribute("href", "/admin/copyright");
    expect(pending).toHaveTextContent("19");
    expect(screen.getByRole("link", { name: /^수집 관리/ })).toHaveAttribute("href", "/admin/crawl");
  });

  it("수집 작업이 없으면 '아직 수집한 적이 없어요' + '수집 시작하기'", async () => {
    renderDashboard(dashboard({ latestJob: null }));
    await findText("아직 수집한 적이 없어요");
    expect(screen.getByRole("link", { name: "수집 시작하기" })).toHaveAttribute("href", "/admin/crawl");
  });

  it("진행 중 작업 카드: '진행 중' · '12 / 50' · 시작 일시 · '보기'", async () => {
    renderDashboard(dashboard({ latestJob: crawlJob(), activeJob: crawlJob() }));
    await findText("최근 수집 작업");
    expectText("진행 중");
    expectText("12 / 50");
    expect(document.body.textContent).toMatch(/시작 \d{4}-\d{2}-\d{2} \d{2}:\d{2}/);
    expect(screen.getByRole("link", { name: /보기/ })).toHaveAttribute("href", "/admin/crawl/12");
  });

  it("완료 작업 카드: '완료' + '성공 46 · 실패 3'", async () => {
    renderDashboard(dashboard({ latestJob: completedCrawlJob() }));
    await findText("최근 수집 작업");
    expectText("완료");
    expectText("성공 46 · 실패 3");
    expectNoText("진행 중");
  });

  it("불러오기 실패 → 숫자 카드 영역에 '연결을 확인해 주세요' + '다시 시도', 바로가기는 살아 있음", async () => {
    renderDashboard(null, { reject: true });
    await findText("연결을 확인해 주세요");
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^곡 관리/ })).toBeInTheDocument();
  });
});
