import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useDebouncedSearchInput } from "./useDebouncedSearchInput.js";

// 내부 구현 세부(관리 목록 3개가 함께 쓰는 검색 디바운스)라 frontend-dev 가 직접 먼저 작성한 테스트.
// URL 쿼리가 상태의 원본이고, 입력은 300ms 뒤 한 번만 URL 로 반영된다.
function Probe({ value, onCommit }) {
  const [text, change] = useDebouncedSearchInput(value, onCommit);
  return <input aria-label="검색" value={text} onChange={(event) => change(event.target.value)} />;
}

function input() {
  return screen.getByLabelText("검색");
}

describe("useDebouncedSearchInput", () => {
  it("바깥 값(URL 쿼리)으로 입력칸을 채운다", () => {
    render(<Probe value="베토" onCommit={vi.fn()} />);
    expect(input()).toHaveValue("베토");
  });

  it("연달아 입력하면 300ms 뒤 마지막 값 하나만 넘긴다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const onCommit = vi.fn();
    render(<Probe value="" onCommit={onCommit} />);

    fireEvent.change(input(), { target: { value: "쇼" } });
    fireEvent.change(input(), { target: { value: "쇼팽" } });
    expect(input()).toHaveValue("쇼팽");
    await vi.advanceTimersByTimeAsync(200);
    expect(onCommit).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(200);
    await waitFor(() => expect(onCommit).toHaveBeenCalledTimes(1));
    expect(onCommit).toHaveBeenCalledWith("쇼팽");
  });

  it("바깥 값이 바뀌면 입력칸이 따라가고 다시 넘기지 않는다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const onCommit = vi.fn();
    const view = render(<Probe value="쇼팽" onCommit={onCommit} />);
    view.rerender(<Probe value="" onCommit={onCommit} />);

    expect(input()).toHaveValue("");
    await vi.advanceTimersByTimeAsync(600);
    expect(onCommit).not.toHaveBeenCalled();
  });
});
