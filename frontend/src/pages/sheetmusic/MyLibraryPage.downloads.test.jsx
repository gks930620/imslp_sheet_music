import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { MyLibraryPage } from "./MyLibraryPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../test/apiMock.js";
import {
  downloadsResponse,
  libraryCounts,
  receivedRow,
  receivedRowRecommendationChanged,
  receivedRowUnavailable,
  receivedRowWithAlternative,
} from "../../test/fixtures.library.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

/**
 * 내 악보 › 받은 악보 탭 — 화면정의 09 §1-2(항목·3상태 표), 기획 05 §3, 인수 조건 8-D 1·5·6·7·8·11·12.
 * 계약: 02 §10-3(ReceivedWorkDTO) · §3-4(다시 받기는 같은 다운로드 문 + HEAD 사전 확인).
 *
 * <b>한 항목에 버튼은 최대 하나다</b>(09 §1-2-2): ① 다시 받기 / ③-a 지금 추천 판본 받기 / ③-b 곡 보기.
 */

const ROUTE = "/piano/library/downloads";

function renderDownloads(items, extraRoutes = []) {
  mockFetch([
    { url: /\/api\/editions\/\d+\/download/, method: "HEAD", raw: "" },
    {
      url: /\/api\/me\/library\/downloads/,
      data: downloadsResponse({ items, counts: libraryCounts(2, items.length) }),
    },
    ...extraRoutes,
  ]);
  return renderWithProviders(<MyLibraryPage />, { route: ROUTE, path: "/:section/library/:tab", auth: "user" });
}

function redownloadLink() {
  return screen.queryByRole("link", { name: /다시 받기/ });
}

describe("받은 악보 항목 — 곡 카드 + 받은 날짜 + 받은 판본 (8-D 1·5)", () => {
  it("곡 카드는 곡 상세로, 날짜와 '받은 판본' 줄이 함께 보인다 (09 D7)", async () => {
    renderDownloads([receivedRow()]);
    await findText("녹턴 2번");

    expect(screen.getByRole("link", { name: /녹턴 2번/ })).toHaveAttribute("href", "/piano/works/23");
    expectText("받은 판본: 전체 악보 · 전곡 · 5쪽 · 1.1MB");
    expectText("받음");
  });

  it("'이력'·'로그'·'다운로드 이력' 이라는 말은 화면에 없다 (기획 05 §0-2)", async () => {
    renderDownloads([receivedRow()]);
    await findText("녹턴 2번");

    expectNoText("이력");
    expectNoText("로그");
  });

  it("받은 판본 정보가 없으면 그 줄을 생략한다 (09 §1-2-2 ③)", async () => {
    renderDownloads([receivedRowUnavailable({ receivedEdition: null })]);
    await findText("녹턴 2번");

    expectNoText("받은 판본:");
  });

  it("0건이면 '로그인한 상태에서' 를 담은 빈 안내와 출구가 보인다 (8-D 11)", async () => {
    renderDownloads([]);
    await findText("아직 받은 악보가 없어요");

    expectText("로그인한 상태에서");
    expectText("받은 악보가 여기 남아요");
    expect(screen.getByRole("link", { name: "인기곡 보러 가기" })).toHaveAttribute("href", "/piano");
  });
});

describe("다시 받기 3상태 (09 §1-2-2)", () => {
  it("① 그대로 받을 수 있으면 '다시 받기' 하나뿐 — 추가 안내 없음", async () => {
    renderDownloads([receivedRow()]);
    await findText("녹턴 2번");

    expect(redownloadLink()).toHaveAttribute("href", "/api/editions/301/download");
    expect(redownloadLink()).toHaveAttribute("download");
    expectNoText("지금 추천 판본은 이것과 달라요");
    expectNoText("그때 받은 악보는 지금 받을 수 없어요");
    expect(screen.queryByRole("link", { name: "곡 보기" })).not.toBeInTheDocument();
  });

  it("② 추천이 달라졌으면 한 줄 + '곡 보기', 다시 받기는 여전히 그때 판본 (8-D 7)", async () => {
    renderDownloads([receivedRowRecommendationChanged()]);
    await findText("지금 추천 판본은 이것과 달라요");

    expect(redownloadLink()).toHaveAttribute("href", "/api/editions/301/download");
    expect(screen.getByRole("link", { name: "곡 보기" })).toHaveAttribute("href", "/piano/works/23");
  });

  it("③-a 못 주면 '그때 받은 악보는 지금 받을 수 없어요' + '지금 추천 판본 받기' (8-D 8)", async () => {
    renderDownloads([receivedRowWithAlternative()]);
    await findText("그때 받은 악보는 지금 받을 수 없어요");

    expect(redownloadLink()).not.toBeInTheDocument();
    const alternative = screen.getByRole("link", { name: /지금 추천 판본 받기/ });
    expect(alternative).toHaveAttribute("href", "/api/editions/302/download");
    expect(alternative).toHaveAttribute("download");
    expectText("지금 추천 판본: 전체 악보 · 전곡 · 12쪽 · 2.4MB");
    expectText("받은 판본: 전체 악보 · 전곡 · 5쪽 · 1.1MB");
  });

  it("③-a 라도 이유를 갈라 말하지 않는다 — '삭제'·'제한' 이라는 말이 없다 (기획 05 §3-3)", async () => {
    renderDownloads([receivedRowWithAlternative()]);
    await findText("그때 받은 악보는 지금 받을 수 없어요");

    expectNoText("삭제");
    expectNoText("제한");
  });

  it("③-b 대체도 없으면 버튼 없이 '곡 보기' 링크만", async () => {
    renderDownloads([receivedRowUnavailable()]);
    await findText("그때 받은 악보는 지금 받을 수 없어요");

    expect(redownloadLink()).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /지금 추천 판본 받기/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "곡 보기" })).toHaveAttribute("href", "/piano/works/23");
  });

  it("못 주는 판본의 다운로드 주소는 화면 어디에도 없다 (8-D 8 — 저작권 게이트에 구멍을 내지 않는다)", async () => {
    renderDownloads([receivedRowUnavailable()]);
    await findText("녹턴 2번");

    const hrefs = Array.from(document.querySelectorAll("a")).map((a) => a.getAttribute("href"));
    expect(hrefs.filter((href) => href?.includes("/api/editions/"))).toEqual([]);
  });
});

describe("다시 받기 동작 (8-D 6·12, 09 §6 S7)", () => {
  it("누르면 같은 주소로 HEAD 를 보내 확인하고, 성공하면 날짜가 '오늘 받음' 으로 바뀐다", async () => {
    const user = userEvent.setup();
    renderDownloads([receivedRow()]);
    await findText("녹턴 2번");

    document.addEventListener("click", (event) => event.preventDefault(), { once: true, capture: true });
    await user.click(redownloadLink());

    await waitFor(() => expect(findCalls(/\/api\/editions\/301\/download/)).toHaveLength(1));
    expect(findCalls(/\/api\/editions\/301\/download/)[0].method).toBe("HEAD");
    await findText("오늘 받음");
  });

  it("시작이 실패하면 그 항목 아래 안내 + '곡 보기', 날짜는 그대로다 (8-D 12)", async () => {
    const user = userEvent.setup();
    renderDownloads(
      [receivedRow()],
      [{ url: /\/api\/editions\/301\/download/, method: "HEAD", status: 503, error: "FILE_UNAVAILABLE" }],
    );
    await findText("녹턴 2번");

    document.addEventListener("click", (event) => event.preventDefault(), { once: true, capture: true });
    await user.click(redownloadLink());

    await findText("지금은 파일을 받을 수 없어요. 잠시 후 다시 시도해 주세요");
    expect(screen.getByRole("link", { name: "곡 보기" })).toHaveAttribute("href", "/piano/works/23");
    expectNoText("오늘 받음");
  });
});

/**
 * 2026-09-21 senior-dev 추가(코드리뷰 발견) — 09 §1-2-2 ② "다시 받기 = **그때 판본**(바꿔치기하지 않는다)".
 * 성공 뒤 상태가 ① 로 바뀌어도 되는 것은 **③-a 뿐**이다(표의 ③ 행: "성공하면 이 항목은 그 자리에서 ① 형태로").
 */
describe("다시 받기 성공 뒤의 상태 (09 §1-2-2)", () => {
  it("② 에서 다시 받아도 '지금 추천 판본은 이것과 달라요' 는 남는다 — 받은 것은 여전히 그때 판본이다", async () => {
    const user = userEvent.setup();
    renderDownloads([receivedRowRecommendationChanged()]);
    await findText("지금 추천 판본은 이것과 달라요");

    document.addEventListener("click", (event) => event.preventDefault(), { once: true, capture: true });
    await user.click(redownloadLink());

    await findText("오늘 받음");
    // 서버는 다음 조회에서도 ② 를 준다(받은 판본 ≠ 지금 추천). 화면이 먼저 ① 로 바꾸면 사용자는 "추천이 같아졌다"
    // 고 읽고, 새로고침하면 안내가 되살아난다 — 같은 사실을 두 번 다르게 말하는 화면이 된다
    expectText("지금 추천 판본은 이것과 달라요");
    expect(redownloadLink()).toHaveAttribute("href", "/api/editions/301/download");
  });

  it("③-a 에서 지금 추천 판본을 받으면 그 자리에서 ① 형태가 된다 — 안내가 사라지고 '다시 받기' 하나만 남는다", async () => {
    const user = userEvent.setup();
    renderDownloads([receivedRowWithAlternative()]);
    await findText("그때 받은 악보는 지금 받을 수 없어요");

    document.addEventListener("click", (event) => event.preventDefault(), { once: true, capture: true });
    await user.click(screen.getByRole("link", { name: /지금 추천 판본 받기/ }));

    await findText("오늘 받음");
    expectNoText("그때 받은 악보는 지금 받을 수 없어요");
    expectNoText("지금 추천 판본:");
    expectText("받은 판본: 전체 악보 · 전곡 · 12쪽 · 2.4MB");
  });
});
