import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useComposerOptions } from "./useComposerOptions.js";
import { mockFetch, findCall } from "../test/apiMock.js";
import { adminComposers, pageResponse } from "../test/fixtures.js";

// 내부 구현 세부(곡 관리·대기함·곡 편집이 함께 쓰는 작곡가 선택지)라 frontend-dev 가 직접 먼저 작성한 테스트.
// 02_API §4-2 + size(최대 200) — 한 번에 채운다.
const COMPOSERS = /\/api\/admin\/composers/;

function Probe() {
  const { options } = useComposerOptions();
  return (
    <ul>
      {options.map((option) => (
        <li key={option.id}>{option.label}</li>
      ))}
    </ul>
  );
}

describe("useComposerOptions", () => {
  it("size=200 으로 한 번 불러 한글 표기(없으면 원어)로 선택지를 만든다", async () => {
    mockFetch([{ url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) }]);
    render(<Probe />);

    await waitFor(() => expect(screen.getByText("베토벤")).toBeInTheDocument());
    expect(findCall(COMPOSERS).params.get("size")).toBe("200");
    expect(screen.getByText("Satie, Erik")).toBeInTheDocument();
  });

  it("불러오지 못하면 빈 선택지로 둔다", async () => {
    mockFetch([{ url: COMPOSERS, method: "GET", reject: true }]);
    render(<Probe />);
    await waitFor(() => expect(findCall(COMPOSERS)).toBeDefined());
    expect(screen.queryByText("베토벤")).not.toBeInTheDocument();
  });
});
