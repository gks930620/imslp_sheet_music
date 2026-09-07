import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { dismissToast, showToast } from "./Toast.jsx";
import { bodyText } from "../../test/text.js";

// 내부 구현 세부(공용 Toast)라 frontend-dev 가 직접 먼저 작성한 테스트.
// 05·06 화면 정의서: 저장/삭제/판정 뒤 Toast 를 띄우는데, 그중 상당수가 "저장 → 목록으로 이동" 이라
// 화면이 언마운트된 뒤에도 문구가 남아야 한다.
afterEach(() => dismissToast());

describe("Toast", () => {
  it("문구를 띄운다", () => {
    showToast("저장했어요");
    expect(screen.getByRole("status")).toHaveTextContent("저장했어요");
  });

  it("띄운 화면이 사라져도 문구는 남는다", () => {
    function Page() {
      return (
        <button type="button" onClick={() => showToast("삭제했어요")}>
          삭제
        </button>
      );
    }
    const view = render(<Page />);
    screen.getByRole("button", { name: "삭제" }).click();
    view.unmount();
    expect(bodyText()).toContain("삭제했어요");
  });

  it("연달아 띄우면 마지막 문구 하나만 남는다", () => {
    showToast("저장했어요");
    showToast("판정했어요");
    expect(screen.getAllByRole("status")).toHaveLength(1);
    expect(bodyText()).toContain("판정했어요");
    expect(bodyText()).not.toContain("저장했어요");
  });

  it("시간이 지나면 스스로 사라진다", async () => {
    vi.useFakeTimers();
    showToast("2개를 판정했어요");
    expect(bodyText()).toContain("2개를 판정했어요");
    await vi.advanceTimersByTimeAsync(4_000);
    expect(bodyText()).not.toContain("2개를 판정했어요");
    vi.useRealTimers();
  });
});
