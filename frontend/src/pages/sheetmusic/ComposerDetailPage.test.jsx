import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ComposerDetailPage } from "./ComposerDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import { composerDetail, composerWorksResponse, workNocturne2 } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 04_작곡가.md 화면 B — /composers/:id?sort=&level=&pages=&downloadable=&page=
// 작곡가 정보 GET /api/composers/9, 곡 목록 GET /api/composers/9/works (검색 결과와 같은 FilterBar·쿼리 이름)
const WORKS = /\/api\/composers\/9\/works/;
const INFO = /\/api\/composers\/9$/;

function renderDetail(route = "/composers/9", { info = composerDetail(), works = composerWorksResponse(), infoStatus = 200 } = {}) {
  mockFetch([
    infoStatus === 200 ? { url: INFO, data: info } : { url: INFO, status: infoStatus, error: "NOT_FOUND", message: "작곡가를 찾을 수 없어요" },
    { url: WORKS, data: works },
  ]);
  return renderWithProviders(<ComposerDetailPage />, { route, path: "/composers/:id" });
}

function lastWorksParams() {
  const calls = findCalls(WORKS);
  return calls[calls.length - 1].params;
}

describe("ComposerDetailPage — 작곡가 정보", () => {
  it("한글 표기 h1 · 원어 · 생몰년 · 국적 · 별칭 · IMSLP 링크", async () => {
    renderDetail();
    await findText("쇼팽");
    expect(screen.getByRole("heading", { level: 1, name: "쇼팽" })).toBeInTheDocument();
    expect(screen.getByText("Chopin, Frédéric")).toBeInTheDocument();
    expectText("1810–1849 · 폴란드");
    expectText("쇼팡이라고도 씁니다");
    const link = screen.getByRole("link", { name: /IMSLP 작곡가 페이지 보기/ });
    expect(link).toHaveAttribute("href", "https://imslp.org/wiki/Category:Chopin,_Frédéric");
    expect(link).toHaveAttribute("target", "_blank");
  });

  it("별칭 여러 개 → '쇼팡, Chopin이라고도 씁니다'; 없으면 줄 생략, IMSLP 링크 없으면 생략", async () => {
    renderDetail("/composers/9", { info: composerDetail({ aliases: ["쇼팡", "Chopin"], imslpUrl: null, nationality: null }) });
    await findText("쇼팡, Chopin이라고도 씁니다");
    expect(screen.queryByRole("link", { name: /IMSLP 작곡가 페이지 보기/ })).not.toBeInTheDocument();
    expectText("1810–1849");
    expectNoText("· 폴란드");
  });

  it("별칭 0개면 '라고도 씁니다' 줄 없음", async () => {
    renderDetail("/composers/9", { info: composerDetail({ aliases: [] }) });
    await findText("쇼팽");
    expectNoText("라고도 씁니다");
  });

  it("한글 표기 없으면 원어가 h1, 원어 줄 생략", async () => {
    renderDetail("/composers/9", { info: composerDetail({ nameKo: null }) });
    await findText("Chopin, Frédéric");
    expect(screen.getByRole("heading", { level: 1, name: "Chopin, Frédéric" })).toBeInTheDocument();
    expect(screen.getAllByText("Chopin, Frédéric")).toHaveLength(1);
  });
});

describe("ComposerDetailPage — 곡 목록·정렬·필터", () => {
  it("건수 '곡 24개', 곡 카드는 작곡가 줄 없이 작품번호만", async () => {
    renderDetail();
    await findText("녹턴 2번");
    expectText("곡 24개");
    expectNoText("곡 24개 중");
    expect(screen.getByRole("link", { name: /녹턴 2번/ })).toHaveAttribute("href", "/works/23");
    expectNoText("쇼팽 (Chopin, Frédéric)");
    expectText("Op.9 No.2");
    expectNoText("으로 찾음");
  });

  it("필터 시 '곡 24개 중 8개'", async () => {
    renderDetail("/composers/9?level=INTERMEDIATE", { works: composerWorksResponse({ totalElements: 8, unfilteredTotal: 24 }) });
    await findText("곡 24개 중 8개");
  });

  it("URL 쿼리를 그대로 곡 목록 API 에 전달한다", async () => {
    renderDetail("/composers/9?sort=opus&level=ELEMENTARY,INTERMEDIATE&pages=GE21&downloadable=true&page=1");
    await findText("녹턴 2번");
    const params = findCall(WORKS).params;
    expect(params.get("sort")).toBe("opus");
    expect(params.get("level")).toBe("ELEMENTARY,INTERMEDIATE");
    expect(params.get("pages")).toBe("GE21");
    expect(params.get("downloadable")).toBe("true");
    expect(params.get("page")).toBe("1");
    expect(screen.getByRole("combobox")).toHaveValue("opus");
    expect(screen.getByRole("button", { name: "초급", pressed: true })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "바로 받기 가능한 곡만" })).toBeChecked();
  });

  it("정렬 select: '다운로드 많은 순'(기본) / '작품번호 순', 바꾸면 sort=opus + page=0", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail("/composers/9?page=1");
    await findText("녹턴 2번");
    const select = screen.getByRole("combobox");
    expect(screen.getByRole("option", { name: "다운로드 많은 순" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "작품번호 순" })).toBeInTheDocument();
    expect(select).toHaveValue("downloads");
    await user.selectOptions(select, "opus");
    expect(getLocation().params.get("sort")).toBe("opus");
    expect(getLocation().params.get("page") ?? "0").toBe("0");
    await waitFor(() => expect(lastWorksParams().get("sort")).toBe("opus"));
  });

  it("난이도 칩·쪽수 칩·체크박스가 검색 결과와 같은 쿼리 이름으로 URL 을 갱신한다", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail();
    await findText("녹턴 2번");
    await user.click(screen.getByRole("button", { name: "초급" }));
    expect(getLocation().params.get("level")).toBe("ELEMENTARY");
    await user.click(screen.getByRole("button", { name: "10쪽 이하" }));
    expect(getLocation().params.get("pages")).toBe("LE10");
    await user.click(screen.getByRole("checkbox", { name: "바로 받기 가능한 곡만" }));
    expect(getLocation().params.get("downloadable")).toBe("true");
    await user.click(screen.getByRole("button", { name: "필터 해제" }));
    expect(getLocation().search.replace(/^\?/, "")).not.toMatch(/level|pages|downloadable/);
  });

  it("페이지 이동 → page 쿼리", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail("/composers/9", { works: composerWorksResponse({ works: [workNocturne2], totalElements: 24 }) });
    await findText("녹턴 2번");
    await user.click(screen.getByRole("button", { name: "2" }));
    expect(getLocation().params.get("page")).toBe("1");
  });
});

describe("ComposerDetailPage — 빈 상태·오류", () => {
  it("공개 곡 0개(필터 없음) → '아직 공개된 곡이 없어요'", async () => {
    renderDetail("/composers/9", { works: composerWorksResponse({ works: [], totalElements: 0, unfilteredTotal: 0 }) });
    await findText("아직 공개된 곡이 없어요");
  });

  it("필터 0건 → '이 조건에 맞는 곡이 없어요' + '필터 해제'", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderDetail("/composers/9?level=BEGINNER", {
      works: composerWorksResponse({ works: [], totalElements: 0, unfilteredTotal: 24 }),
    });
    await findText("이 조건에 맞는 곡이 없어요");
    await user.click(screen.getByRole("button", { name: "필터 해제" }));
    expect(getLocation().params.has("level")).toBe(false);
  });

  it("페이지 범위 밖 → '이 페이지에는 곡이 없어요' + '첫 페이지로'", async () => {
    renderDetail("/composers/9?page=99", { works: composerWorksResponse({ works: [], totalElements: 24, unfilteredTotal: 24, page: 99 }) });
    await findText("이 페이지에는 곡이 없어요");
    expect(screen.getByRole("button", { name: "첫 페이지로" })).toBeInTheDocument();
  });

  it("404 → '찾을 수 없는 페이지예요'", async () => {
    renderDetail("/composers/9", { infoStatus: 404 });
    await findText("찾을 수 없는 페이지예요");
  });

  it("불러오기 실패 → '연결을 확인해 주세요' + '다시 시도'", async () => {
    mockFetch([{ url: INFO, reject: true }, { url: WORKS, reject: true }]);
    renderWithProviders(<ComposerDetailPage />, { route: "/composers/9", path: "/composers/:id" });
    await findText("연결을 확인해 주세요");
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});
