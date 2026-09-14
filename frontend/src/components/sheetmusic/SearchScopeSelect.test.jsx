import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { SearchScopeSelect } from "./SearchScopeSelect.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";

// 화면정의 08 §4-1 검색 기준 세그먼트 · 기획 04 §4
// 값은 API 계약(02 §3-1)과 같은 대문자다: ALL / TITLE / COMPOSER

function group() {
  return within(screen.getByRole("radiogroup", { name: "검색 기준" }));
}

describe("SearchScopeSelect — 형태", () => {
  it("전체·곡명·작곡가 세 칸이 라디오로 있고, 항상 정확히 하나가 선택돼 있다", () => {
    renderWithProviders(<SearchScopeSelect value="ALL" onChange={vi.fn()} />);
    const radios = group().getAllByRole("radio");
    expect(radios.map((radio) => radio.textContent.trim())).toEqual(["전체", "곡명", "작곡가"]);
    expect(radios.filter((radio) => radio.getAttribute("aria-checked") === "true")).toHaveLength(1);
  });

  it("드롭다운(select)이 아니다 — 세 선택지가 누르기 전에 다 보여야 한다 (08 §4-2)", () => {
    renderWithProviders(<SearchScopeSelect value="ALL" onChange={vi.fn()} />);
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it.each([
    ["ALL", "전체"],
    ["TITLE", "곡명"],
    ["COMPOSER", "작곡가"],
  ])("value=%s 면 '%s' 칸이 선택돼 보인다", (value, label) => {
    renderWithProviders(<SearchScopeSelect value={value} onChange={vi.fn()} />);
    expect(group().getByRole("radio", { name: label })).toHaveAttribute("aria-checked", "true");
  });

  it.each([undefined, null, "", "xyz", "title", "TITLE_KO"])(
    "알 수 없는 값(%s)이면 '전체'가 선택돼 보인다 — 오류 문구를 만들지 않는다 (인수 조건 8-F 4)",
    (value) => {
      renderWithProviders(<SearchScopeSelect value={value} onChange={vi.fn()} />);
      expect(group().getByRole("radio", { name: "전체" })).toHaveAttribute("aria-checked", "true");
      expect(document.body.textContent).not.toContain("올바르지");
    },
  );
});

describe("SearchScopeSelect — 고르면", () => {
  it("고른 값을 대문자 계약값으로 알린다", async () => {
    const onChange = vi.fn();
    renderWithProviders(<SearchScopeSelect value="ALL" onChange={onChange} />);
    await userEvent.click(group().getByRole("radio", { name: "곡명" }));
    expect(onChange).toHaveBeenCalledWith("TITLE");
  });

  it("이미 선택된 칸을 다시 눌러도 알리지 않는다 — 같은 검색을 두 번 하지 않는다", async () => {
    const onChange = vi.fn();
    renderWithProviders(<SearchScopeSelect value="TITLE" onChange={onChange} />);
    await userEvent.click(group().getByRole("radio", { name: "곡명" }));
    expect(onChange).not.toHaveBeenCalled();
  });
});
