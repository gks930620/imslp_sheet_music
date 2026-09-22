import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MyLibraryPage } from "./MyLibraryPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { fakeResponse, findCalls, mockFetch } from "../../test/apiMock.js";
import { downloadsResponse, libraryCounts, libraryWorkNocturne, receivedRow } from "../../test/fixtures.library.js";
import { expectNoText, expectText, findText } from "../../test/text.js";
import { findTextOnFakeClock, tick } from "../../test/fakeClock.js";

/**
 * 내 악보 › 받은 악보 "다시 받기" 의 진행 표시 — 화면정의 09 §1-2-1("`03` 과 같다"), 03_기술결정 §25-2 → §28.
 *
 * 정의서가 "`03` 과 같다" 라고 쓴 이상, 이 화면의 단언은 곡 상세
 * (`WorkDetailPage.downloadProgress.test.jsx`)와 **같은 모양**이어야 한다. 한 화면만 고치면
 * 같은 동작이 화면마다 다르게 보인다 — §25-2 가 "한 곳만 고칠 수 없는 일" 이라고 적은 이유다.
 *
 * 여기에만 있는 것 둘:
 *   ⑴ 목록이라 **행마다 따로** 진행한다 (한 행이 받는 중이어도 다른 행은 멀쩡하다)
 *   ⑵ 성공은 날짜를 `오늘 받음` 으로 바꾼다 — 그래서 **10초가 지나 도착한 응답**이 날짜를 건드리면 안 된다
 */

const ROUTE = "/piano/library/downloads";
const FIRST_URL = "/api/editions/301/download";
const SECOND_URL = "/api/editions/302/download";
const FAILED_TEXT = "지금은 파일을 받을 수 없어요";
const RELEASE_MS = 10_000;

const secondRow = () =>
  receivedRow({
    work: { ...libraryWorkNocturne, id: 24, titleKo: "엘리제를 위하여" },
    redownloadUrl: SECOND_URL,
  });

function renderDownloads(items, routes = []) {
  const fetchMock = mockFetch([
    { url: /\/api\/me\/library\/downloads/, data: downloadsResponse({ items, counts: libraryCounts(0, items.length) }) },
    { url: /\/api\/editions\/\d+\/download/, method: "HEAD", raw: "" },
    ...routes,
  ]);
  renderWithProviders(<MyLibraryPage />, { route: ROUTE, path: "/:section/library/:tab", auth: "user" });
  return fetchMock;
}

/** 라벨이 `받는 중…` 으로 바뀌어도 같은 버튼을 가리키도록 이름이 아니라 자리로 잡는다 */
function receiveButtons() {
  return Array.from(document.querySelectorAll(".library-receive-btn"));
}

function receiveButton(index = 0) {
  return receiveButtons()[index];
}

function spinner(index = 0) {
  return receiveButton(index).querySelector(".btn-spinner");
}

/** 항목의 "받음" 날짜 한 줄 — 늦게 온 응답이 이 줄을 건드리지 않았는지 보는 자리 */
function receivedDate(index = 0) {
  return document.querySelectorAll(".library-receive-date")[index]?.textContent;
}

function stopNavigation(event) {
  event.preventDefault();
}

beforeEach(() => document.addEventListener("click", stopNavigation));
afterEach(() => document.removeEventListener("click", stopNavigation));

const never = () => new Promise(() => {});

describe("받은 악보 다시 받기 — 진행 표시 (09 §1-2-1, 03 §251 과 같다)", () => {
  it("누르기 전에는 스피너도 잠금도 없다", async () => {
    renderDownloads([receivedRow()]);
    await findText("녹턴 2번");

    expect(receiveButton()).toHaveTextContent("다시 받기");
    expect(receiveButton()).not.toHaveAttribute("aria-disabled");
    expect(spinner()).toBeNull();
  });

  it("받는 중에는 스피너가 돌고 버튼이 잠긴다 — 곡 상세와 같은 표시다", async () => {
    renderDownloads([receivedRow()], [{ url: FIRST_URL, method: "HEAD", handler: never }]);
    await findText("녹턴 2번");

    receiveButton().click();

    await findText("받는 중…");
    expect(receiveButton()).toHaveAttribute("aria-disabled", "true");
    expect(receiveButton().className).toContain("is-disabled");
    expect(spinner()).not.toBeNull();
    expect(spinner()).toHaveAttribute("aria-hidden", "true");
    expect(receiveButton()).toHaveAttribute("href", FIRST_URL);
  });

  it("받는 중 다시 눌러도 HEAD 는 한 번만 간다", async () => {
    const fetchMock = renderDownloads([receivedRow()], [{ url: FIRST_URL, method: "HEAD", handler: never }]);
    await findText("녹턴 2번");

    receiveButton().click();
    await findText("받는 중…");
    receiveButton().click();

    expect(findCalls(FIRST_URL, fetchMock)).toHaveLength(1);
  });

  it("한 행이 받는 중이어도 다른 행은 멀쩡하다 — 진행은 행마다 따로다", async () => {
    renderDownloads([receivedRow(), secondRow()], [{ url: FIRST_URL, method: "HEAD", handler: never }]);
    await findText("엘리제를 위하여");

    receiveButton(0).click();

    await findText("받는 중…");
    expect(receiveButton(1)).toHaveTextContent("다시 받기");
    expect(receiveButton(1)).not.toHaveAttribute("aria-disabled");
    expect(spinner(1)).toBeNull();
  });

  it("응답이 끝내 오지 않아도 10초 뒤 풀린다 — 실패 안내도, 날짜 갱신도 없다", async () => {
    // 가짜 시계는 **렌더보다 먼저** 깐다 — 10초 타이머는 클릭 시점에 잡히고, 가짜 시계는 설치 이후에
    // 잡힌 타이머만 가로챈다(test/fakeClock.js). 곡 상세 쪽과 같은 순서다.
    vi.useFakeTimers();
    renderDownloads([receivedRow()], [{ url: FIRST_URL, method: "HEAD", handler: never }]);
    await findTextOnFakeClock("녹턴 2번");
    const dateBefore = receivedDate();

    receiveButton().click();
    await tick();
    expectText("받는 중…");
    expect(receiveButton()).toHaveAttribute("aria-disabled", "true");

    await tick(RELEASE_MS - 500);
    expectText("받는 중…"); // 9.5초까지는 아직 잠겨 있다 — 푸는 시점이 정말 10초인지 본다
    expect(spinner()).not.toBeNull();

    await tick(600);

    expect(receiveButton()).toHaveTextContent("다시 받기");
    expect(receiveButton()).not.toHaveAttribute("aria-disabled");
    expect(spinner()).toBeNull();
    expectNoText(FAILED_TEXT);
    expectNoText("오늘 받음"); // 받았는지 모르는 상태다 — 원장에 없는 일을 화면이 말하지 않는다
    expect(receivedDate()).toBe(dateBefore);
  });

  it("10초가 지나 도착한 200 은 날짜를 바꾸지 않는다 — 늦게 온 답은 버린다", async () => {
    let open;
    const door = new Promise((resolve) => {
      open = resolve;
    });
    let responded = false;
    vi.useFakeTimers();
    renderDownloads(
      [receivedRow()],
      [
        {
          url: FIRST_URL,
          method: "HEAD",
          handler: async () => {
            await door;
            responded = true;
            return fakeResponse("", 200);
          },
        },
      ],
    );
    await findTextOnFakeClock("녹턴 2번");
    const dateBefore = receivedDate();

    receiveButton().click();
    await tick();
    expectText("받는 중…");

    await tick(RELEASE_MS + 100);
    expect(receiveButton()).toHaveTextContent("다시 받기");

    open();
    await tick();

    expect(responded).toBe(true); // 응답은 실제로 도착했다 — 이 확인이 없으면 아래 단언이 공허해진다
    expectNoText("오늘 받음");
    expect(receivedDate()).toBe(dateBefore);

    // 대조군 — 같은 화면에서 **제때** 온 200 은 날짜를 바꾼다. 그러니 위가 그대로인 이유는 "늦었기 때문" 이다
    receiveButton().click();
    await tick();
    expectText("오늘 받음");
  });
});
