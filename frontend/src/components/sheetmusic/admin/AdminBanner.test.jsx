import { screen, waitFor } from "@testing-library/react";
import { AdminBanner } from "./AdminBanner.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../../test/apiMock.js";
import { crawlJob } from "../../../test/fixtures.js";
import { findText, expectNoText } from "../../../test/text.js";

// 00_공통_레이아웃_토큰.md §3-14 AdminBanner, 05 공통, 02_API §7(진입 시 + 30초 간격)

describe("AdminBanner", () => {
  it("진행 중(RUNNING) 작업이 있으면 '수집 진행 중 12/50 — 보기'", async () => {
    mockFetch([{ url: "/api/admin/crawl/jobs/active", data: crawlJob() }]);
    renderWithProviders(<AdminBanner />, { auth: "admin" });
    await findText("수집 진행 중 12/50 — 보기");
    expect(screen.getByRole("link", { name: "보기" })).toHaveAttribute("href", "/admin/crawl/12");
  });

  it("일시 정지(PAUSED)면 '수집 일시 정지 — IMSLP가 응답하지 않아요 — 보기'", async () => {
    mockFetch([{ url: "/api/admin/crawl/jobs/active", data: crawlJob({ status: "PAUSED", pausedUntil: "2026-09-06T05:35:00Z" }) }]);
    renderWithProviders(<AdminBanner />, { auth: "admin" });
    await findText("수집 일시 정지 — IMSLP가 응답하지 않아요 — 보기");
  });

  it("진행 중 작업이 없으면(null) 아무것도 렌더하지 않는다", async () => {
    mockFetch([{ url: "/api/admin/crawl/jobs/active", data: null }]);
    const { container } = renderWithProviders(<AdminBanner />, { auth: "admin" });
    await waitFor(() => expect(findCalls(/\/api\/admin\/crawl\/jobs\/active/)).toHaveLength(1));
    expectNoText("수집");
    // 컨테이너에는 라우터 프로브 1개만 남아야 한다 (띠 DOM 없음)
    expect(container.children).toHaveLength(1);
  });

  it("조회 실패는 조용히 무시한다 (띠 없음, 오류 문구 없음)", async () => {
    mockFetch([{ url: "/api/admin/crawl/jobs/active", reject: true }]);
    renderWithProviders(<AdminBanner />, { auth: "admin" });
    await waitFor(() => expect(findCalls(/\/api\/admin\/crawl\/jobs\/active/)).toHaveLength(1));
    expectNoText("연결을 확인해 주세요");
    expectNoText("수집");
  });

  it("30초마다 다시 조회한다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockFetch([{ url: "/api/admin/crawl/jobs/active", data: crawlJob() }]);
    renderWithProviders(<AdminBanner />, { auth: "admin" });
    await findText("수집 진행 중 12/50");
    expect(findCalls(/\/api\/admin\/crawl\/jobs\/active/)).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(30_000);
    await waitFor(() => expect(findCalls(/\/api\/admin\/crawl\/jobs\/active/)).toHaveLength(2));
  });
});
