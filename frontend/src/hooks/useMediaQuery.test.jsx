import { act, render } from "@testing-library/react";
import { vi } from "vitest";
import { useMediaQuery } from "./useMediaQuery.js";

// frontend-dev 내부 단위 — 00 §3-1 "360px 대응 짧은 자리 문구" 를 위한 matchMedia 구독.

function Probe({ query }) {
  const matches = useMediaQuery(query);
  return <span data-testid="m">{String(matches)}</span>;
}

function stubMatchMedia(initial) {
  const listeners = new Set();
  const mql = {
    matches: initial,
    media: "",
    addEventListener: (_, fn) => listeners.add(fn),
    removeEventListener: (_, fn) => listeners.delete(fn),
  };
  vi.stubGlobal("matchMedia", vi.fn(() => mql));
  return {
    fire(matches) {
      mql.matches = matches;
      listeners.forEach((fn) => fn({ matches }));
    },
    listeners,
  };
}

describe("useMediaQuery", () => {
  it("현재 값을 돌려주고, 바뀌면 다시 그린다", () => {
    const media = stubMatchMedia(false);
    const { getByTestId } = render(<Probe query="(max-width: 599px)" />);
    expect(getByTestId("m").textContent).toBe("false");
    act(() => media.fire(true));
    expect(getByTestId("m").textContent).toBe("true");
  });

  it("언마운트하면 구독을 푼다", () => {
    const media = stubMatchMedia(true);
    const { unmount } = render(<Probe query="(max-width: 599px)" />);
    expect(media.listeners.size).toBe(1);
    unmount();
    expect(media.listeners.size).toBe(0);
  });
});
