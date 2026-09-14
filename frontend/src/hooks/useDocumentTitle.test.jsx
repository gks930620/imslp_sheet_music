import { render } from "@testing-library/react";
import { useDocumentTitle } from "./useDocumentTitle.js";

// frontend-dev 내부 단위 — 03 §21-7: 라이브러리 없이 document.title 대입. 언마운트 시 복귀 없음.

function Titled({ title }) {
  useDocumentTitle(title);
  return null;
}

describe("useDocumentTitle", () => {
  it("문자열을 주면 탭 제목이 된다", () => {
    render(<Titled title="쉬운악보 — 피아노" />);
    expect(document.title).toBe("쉬운악보 — 피아노");
  });

  it("바뀌면 따라간다", () => {
    const { rerender } = render(<Titled title="첫 제목" />);
    rerender(<Titled title="둘째 제목" />);
    expect(document.title).toBe("둘째 제목");
  });

  it("빈 값이면 건드리지 않는다 (아직 모를 때 이전 제목 유지)", () => {
    document.title = "이전 제목";
    render(<Titled title={null} />);
    expect(document.title).toBe("이전 제목");
  });

  it("언마운트해도 기본값으로 되돌리지 않는다 — 다음 화면이 곧바로 자기 제목을 쓴다", () => {
    const { unmount } = render(<Titled title="남는 제목" />);
    unmount();
    expect(document.title).toBe("남는 제목");
  });
});
