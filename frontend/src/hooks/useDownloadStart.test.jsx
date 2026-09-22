import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DOWNLOADING_LABEL, DOWNLOAD_RELEASE_MS, useDownloadStart } from "./useDownloadStart.js";
import { fakeResponse, findCalls, mockFetch } from "../test/apiMock.js";

/**
 * 다운로드 시작 표시 공용 훅 — 화면정의 03 §251 · 09 §1-2-1, 03_기술결정 §25-2 → §28.
 *
 * 두 화면(곡 상세 "PDF 받기" · 내 악보 "다시 받기")이 **같은 규칙**을 쓰게 만드는 자리다.
 * 규칙이 화면마다 갈리면 같은 제품 안에서 같은 동작이 다르게 보인다(§25-2 가 미룬 이유).
 *
 * 이 훅이 책임지는 네 가지
 *   ⑴ 진행 중 표시 — `받는 중…` + `aria-disabled` + `is-disabled`
 *   ⑵ 끝 판정   — 같은 주소로 보낸 `HEAD`(02 §3-4 · 03 §12)의 응답
 *   ⑶ 안전장치  — **클릭 시각 기준** 10초가 지나면 무조건 푼다(실패로 치지 않는다)
 *   ⑷ 늦게 온 답 — 10초가 지나 도착한 응답은 성공이든 실패든 **버린다**
 *
 * `<a href download>` 라 진짜 `disabled` 속성이 없다(02 §7: fetch 로 blob 받지 않는다).
 * 그래서 "잠김" 은 `aria-disabled` + 재클릭 `preventDefault` 로 표현하고, **href 는 절대 지우지 않는다** —
 * 첫 클릭의 네이티브 다운로드가 이미 진행 중이기 때문이다.
 */

const URL = "/api/editions/301/download";
const IDLE_LABEL = "PDF 받기";

function Harness({ url = URL, className = "btn btn-primary", onSuccess } = {}) {
  const download = useDownloadStart({ url, className, onSuccess });
  return (
    <>
      <a {...download.linkProps}>{download.busy ? DOWNLOADING_LABEL : IDLE_LABEL}</a>
      <span data-testid="state">{download.state}</span>
      <span data-testid="flags">{`${download.busy}/${download.failed}`}</span>
    </>
  );
}

function link() {
  return screen.getByRole("link");
}

function state() {
  return screen.getByTestId("state").textContent;
}

function flags() {
  return screen.getByTestId("flags").textContent;
}

/**
 * jsdom 은 링크의 기본 동작(내비게이션)을 구현하지 않아 그대로 두면 에러를 뱉는다.
 * document **버블** 단계에서 막는다 — React 의 처리기는 그 전(루트 컨테이너)에서 끝나므로,
 * 여기서 읽는 `defaultPrevented` 가 곧 "훅이 기본 동작을 막았는가" 다.
 */
const clicks = [];
function stopNavigation(event) {
  clicks.push(event.defaultPrevented);
  event.preventDefault();
}

beforeEach(() => {
  clicks.length = 0;
  document.addEventListener("click", stopNavigation);
});

afterEach(() => {
  document.removeEventListener("click", stopNavigation);
});

/** 응답을 테스트가 직접 여는 문 — 전이 상태를 확정적으로 관찰한다 */
function gate() {
  let open;
  const promise = new Promise((resolve) => {
    open = resolve;
  });
  return { promise, open };
}

const never = () => new Promise(() => {});

function mockHead(handler) {
  return mockFetch([{ url: URL, method: "HEAD", handler }]);
}

describe("useDownloadStart — 계약값", () => {
  it("진행 중 문구와 자동 복귀 시간은 두 화면이 나눠 쓰는 상수다", () => {
    expect(DOWNLOADING_LABEL).toBe("받는 중…");
    expect(DOWNLOAD_RELEASE_MS).toBe(10_000);
  });
});

describe("useDownloadStart — 누르기 전", () => {
  it("idle 이고 링크는 주소·download 속성을 그대로 들고 있다", () => {
    mockHead(never);
    render(<Harness />);

    expect(state()).toBe("idle");
    expect(link()).toHaveAttribute("href", URL);
    expect(link()).toHaveAttribute("download");
    expect(link()).not.toHaveAttribute("aria-disabled");
    expect(link()).toHaveTextContent(IDLE_LABEL);
    expect(link().className).toBe("btn btn-primary");
  });
});

describe("useDownloadStart — 받는 중 (03 §251 · 09 §1-2-1)", () => {
  it("누르면 받는 중이 되고 aria-disabled·is-disabled 가 붙는다", async () => {
    mockHead(never);
    render(<Harness />);

    link().click();

    await waitFor(() => expect(state()).toBe("checking"));
    expect(flags()).toBe("true/false");
    expect(link()).toHaveAttribute("aria-disabled", "true");
    expect(link().className).toBe("btn btn-primary is-disabled");
    expect(link()).toHaveTextContent(DOWNLOADING_LABEL);
  });

  it("받는 중에도 href 는 그대로 남는다 — 이미 시작된 네이티브 다운로드를 끊지 않는다", async () => {
    mockHead(never);
    render(<Harness />);

    link().click();

    await waitFor(() => expect(state()).toBe("checking"));
    expect(link()).toHaveAttribute("href", URL);
    expect(link()).toHaveAttribute("download");
  });

  it("첫 클릭의 기본 동작은 막지 않는다 — 브라우저 다운로드가 그대로 시작돼야 한다", async () => {
    mockHead(never);
    render(<Harness />);

    link().click();

    await waitFor(() => expect(state()).toBe("checking"));
    expect(clicks).toEqual([false]);
  });

  it("받는 중 다시 눌러도 요청은 한 번뿐이고 그 클릭의 기본 동작은 막는다", async () => {
    const fetchMock = mockHead(never);
    render(<Harness />);

    link().click();
    await waitFor(() => expect(state()).toBe("checking"));
    link().click();

    expect(findCalls(URL, fetchMock)).toHaveLength(1);
    expect(clicks).toEqual([false, true]);
    expect(state()).toBe("checking");
  });
});

describe("useDownloadStart — 끝 판정은 HEAD 응답 (03 §12)", () => {
  it("200 이면 idle 로 돌아오고 onSuccess 를 한 번 부른다", async () => {
    const onSuccess = vi.fn();
    const door = gate();
    mockHead(async () => {
      await door.promise;
      return fakeResponse("", 200);
    });
    render(<Harness onSuccess={onSuccess} />);

    link().click();
    await waitFor(() => expect(state()).toBe("checking"));
    door.open();

    await waitFor(() => expect(state()).toBe("idle"));
    expect(onSuccess).toHaveBeenCalledTimes(1);
    expect(link()).not.toHaveAttribute("aria-disabled");
    expect(link().className).toBe("btn btn-primary");
  });

  it("실패 응답이면 failed 가 되고 onSuccess 는 부르지 않는다", async () => {
    const onSuccess = vi.fn();
    mockFetch([{ url: URL, method: "HEAD", status: 503, error: "FILE_UNAVAILABLE" }]);
    render(<Harness onSuccess={onSuccess} />);

    link().click();

    await waitFor(() => expect(state()).toBe("failed"));
    expect(flags()).toBe("false/true");
    expect(onSuccess).not.toHaveBeenCalled();
    expect(link()).not.toHaveAttribute("aria-disabled"); // 다시 누를 수 있어야 한다
  });

  it("네트워크 오류도 failed 다", async () => {
    mockFetch([{ url: URL, method: "HEAD", reject: true }]);
    render(<Harness />);

    link().click();

    await waitFor(() => expect(state()).toBe("failed"));
  });

  it("실패한 뒤 다시 누르면 받는 중으로 돌아가고 실패 표시가 풀린다", async () => {
    let calls = 0;
    mockHead(() => {
      calls += 1;
      return calls === 1 ? fakeResponse("", 503) : never();
    });
    render(<Harness />);

    link().click();
    await waitFor(() => expect(state()).toBe("failed"));
    link().click();

    await waitFor(() => expect(state()).toBe("checking"));
    expect(flags()).toBe("true/false");
  });
});

describe("useDownloadStart — 10초 안전장치 (03 §251 '최대 10초 후 자동 복귀')", () => {
  it("응답이 오지 않아도 10초 뒤 풀린다 — 재는 기준은 클릭(요청 시작) 시각이다", async () => {
    vi.useFakeTimers();
    mockHead(never);
    render(<Harness />);

    link().click();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(state()).toBe("checking");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(DOWNLOAD_RELEASE_MS - 500);
    });
    expect(state()).toBe("checking"); // 9.5초까지는 아직 받는 중이다

    await act(async () => {
      await vi.advanceTimersByTimeAsync(600);
    });
    expect(state()).toBe("idle");
    expect(link()).not.toHaveAttribute("aria-disabled");
  });

  it("10초 복귀는 실패 판정이 아니다 — failed 가 되지 않는다", async () => {
    vi.useFakeTimers();
    const onSuccess = vi.fn();
    mockHead(never);
    render(<Harness onSuccess={onSuccess} />);

    link().click();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(DOWNLOAD_RELEASE_MS + 100);
    });

    expect(flags()).toBe("false/false");
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("10초가 지나 도착한 성공 응답은 버린다 — onSuccess 를 뒤늦게 부르지 않는다", async () => {
    vi.useFakeTimers();
    const onSuccess = vi.fn();
    const door = gate();
    mockHead(async () => {
      await door.promise;
      return fakeResponse("", 200);
    });
    render(<Harness onSuccess={onSuccess} />);

    link().click();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(DOWNLOAD_RELEASE_MS + 100);
    });
    expect(state()).toBe("idle");

    door.open();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(onSuccess).not.toHaveBeenCalled();
    expect(state()).toBe("idle");
  });

  it("10초가 지나 도착한 실패 응답도 버린다 — 안내가 뒤늦게 튀어나오지 않는다", async () => {
    vi.useFakeTimers();
    const door = gate();
    mockHead(async () => {
      await door.promise;
      return fakeResponse("", 503);
    });
    render(<Harness />);

    link().click();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(DOWNLOAD_RELEASE_MS + 100);
    });

    door.open();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(state()).toBe("idle");
    expect(flags()).toBe("false/false");
  });

  it("응답이 먼저 오면 10초 타이머는 남지 않는다", async () => {
    vi.useFakeTimers();
    mockHead(async () => fakeResponse("", 200));
    render(<Harness />);

    link().click();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(state()).toBe("idle");
    expect(vi.getTimerCount()).toBe(0);
  });

  it("받는 중에 화면이 사라지면 타이머도 함께 사라진다", async () => {
    vi.useFakeTimers();
    mockHead(never);
    const view = render(<Harness />);

    link().click();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(vi.getTimerCount()).toBe(1);

    view.unmount();

    expect(vi.getTimerCount()).toBe(0);
  });
});
