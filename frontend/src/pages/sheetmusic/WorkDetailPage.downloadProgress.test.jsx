import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { fakeResponse, findCalls, mockFetch } from "../../test/apiMock.js";
import { workDetail } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";
import { findTextOnFakeClock, tick } from "../../test/fakeClock.js";

/**
 * 곡 상세 "PDF 받기" 의 진행 표시 — 화면정의 03 §251, 03_기술결정 §25-2 → §28.
 *
 * 지금은 글자만 `받는 중…` 으로 바뀐다. 정의서가 요구하는 **스피너 · `disabled` · 10초 자동 복귀**가 없다.
 * 규칙 자체는 `useDownloadStart`(hooks/useDownloadStart.test.jsx)가 잠그고, 이 파일은
 * **이 화면이 그 훅을 실제로 쓰는지**와 마크업 약속(`.btn-spinner`)을 본다 —
 * 내 악보 쪽(`MyLibraryPage.downloadProgress.test.jsx`)과 **글자 하나까지 같은 단언**이어야 한다.
 */

const DOWNLOAD_URL = "/api/editions/301/download";
const IDLE_LABEL = "PDF 받기 · 2.4MB";
const FAILED_TEXT = "지금은 파일을 받을 수 없어요";
const RELEASE_MS = 10_000;

function renderDetail(headRule) {
  return mockFetch([
    { url: /\/api\/works\/21(\?|$)/, data: { ...workDetail(), section: "PIANO" } },
    { url: DOWNLOAD_URL, method: "HEAD", ...headRule },
  ]);
}

function render() {
  return renderWithProviders(<WorkDetailPage />, { route: "/piano/works/21", path: "/:section/works/:id" });
}

function downloadLink() {
  return screen.getByRole("link", { name: /PDF 받기|받는 중/ });
}

function spinner() {
  return downloadLink().querySelector(".btn-spinner");
}

// jsdom 은 링크 기본 동작을 구현하지 않는다 — 버블 단계에서 막는다(훅의 preventDefault 관찰을 가리지 않는 자리)
function stopNavigation(event) {
  event.preventDefault();
}

beforeEach(() => document.addEventListener("click", stopNavigation));
afterEach(() => document.removeEventListener("click", stopNavigation));

const never = () => new Promise(() => {});

describe("곡 상세 다운로드 진행 표시 (03 §251)", () => {
  it("누르기 전에는 스피너도 잠금도 없다", async () => {
    renderDetail({ raw: "" });
    render();
    await findText("추천 판본");

    expect(downloadLink()).toHaveTextContent(IDLE_LABEL);
    expect(downloadLink()).not.toHaveAttribute("aria-disabled");
    expect(spinner()).toBeNull();
  });

  it("받는 중에는 스피너가 돌고 버튼이 잠긴다 — 문구만 바뀌는 것이 아니다", async () => {
    renderDetail({ handler: never });
    render();
    await findText("추천 판본");

    downloadLink().click();

    await findText("받는 중…");
    expect(downloadLink()).toHaveAttribute("aria-disabled", "true");
    expect(downloadLink().className).toContain("is-disabled");
    expect(spinner()).not.toBeNull();
    expect(spinner()).toHaveAttribute("aria-hidden", "true");
    // 링크는 살아 있어야 한다 — 첫 클릭의 네이티브 다운로드가 이 순간 진행 중이다
    expect(downloadLink()).toHaveAttribute("href", DOWNLOAD_URL);
  });

  it("받는 중 다시 눌러도 HEAD 는 한 번만 간다", async () => {
    const fetchMock = renderDetail({ handler: never });
    render();
    await findText("추천 판본");

    downloadLink().click();
    await findText("받는 중…");
    downloadLink().click();

    expect(findCalls(DOWNLOAD_URL, fetchMock)).toHaveLength(1);
  });

  it("응답이 오면 스피너·잠금이 풀리고 원래 문구로 돌아온다", async () => {
    let open;
    const door = new Promise((resolve) => {
      open = resolve;
    });
    renderDetail({
      handler: async () => {
        await door;
        return fakeResponse("", 200);
      },
    });
    render();
    await findText("추천 판본");

    downloadLink().click();
    await findText("받는 중…");
    open();

    await findText(IDLE_LABEL);
    expect(downloadLink()).not.toHaveAttribute("aria-disabled");
    expect(spinner()).toBeNull();
  });

  it("응답이 끝내 오지 않아도 10초 뒤 버튼이 풀린다 — 실패 안내는 띄우지 않는다", async () => {
    // 가짜 시계는 **렌더보다 먼저** 깐다. 10초 타이머는 클릭 시점에 잡히는데, 가짜 시계는 설치 이후에
    // 잡힌 타이머만 가로채기 때문이다(test/fakeClock.js). 그 대가로 findText 대신 시계를 직접 돌린다.
    vi.useFakeTimers();
    renderDetail({ handler: never });
    render();
    await findTextOnFakeClock("추천 판본");

    downloadLink().click();
    await tick();
    expectText("받는 중…");
    expect(downloadLink()).toHaveAttribute("aria-disabled", "true");

    await tick(RELEASE_MS - 500);
    expectText("받는 중…"); // 9.5초까지는 아직 잠겨 있다 — 푸는 시점이 정말 10초인지 본다
    expect(spinner()).not.toBeNull();

    await tick(600);

    expect(downloadLink()).toHaveTextContent(IDLE_LABEL);
    expect(downloadLink()).not.toHaveAttribute("aria-disabled");
    expect(spinner()).toBeNull();
    expectNoText(FAILED_TEXT); // 10초 복귀는 "모른다" 이지 "실패했다" 가 아니다
  });
});
