import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CrawlCheckTable } from "./CrawlCheckTable.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { crawlCheckResponse } from "../../../test/fixtures.js";
import { expectText } from "../../../test/text.js";

// 07_관리자_수집.md 화면 A 판정 표.
// props: items(CrawlCheckItemDTO[]), refreshSeqs(number[] — '정보만 다시 가져오기' 켠 seq), onToggleRefresh(seq, checked)

function row(seq) {
  return screen.getByTestId(`crawl-check-row-${seq}`);
}

function renderTable(props = {}) {
  const onToggleRefresh = vi.fn();
  const utils = renderWithProviders(
    <CrawlCheckTable items={crawlCheckResponse.items} refreshSeqs={[]} onToggleRefresh={onToggleRefresh} {...props} />,
    { auth: "admin" },
  );
  return { ...utils, onToggleRefresh };
}

describe("CrawlCheckTable — 판정 5종 문구", () => {
  it("NEW → '수집 예정'", () => {
    renderTable();
    expect(within(row(1)).getByText("수집 예정")).toBeInTheDocument();
    expect(within(row(1)).queryByText("등록된 곡에 판본 붙임")).not.toBeInTheDocument();
  });

  it("ATTACH → '수집 예정' + 보조 문구 '등록된 곡에 판본 붙임' (곡 링크 새 탭)", () => {
    renderTable();
    expect(within(row(2)).getByText("수집 예정")).toBeInTheDocument();
    const link = within(row(2)).getByRole("link", { name: /등록된 곡에 판본 붙임/ });
    expect(link).toHaveAttribute("href", "/admin/works/22");
    expect(link).toHaveAttribute("target", "_blank");
  });

  it("EXISTS → '이미 있음 — 건너뜀' + 체크박스 '정보만 다시 가져오기'", () => {
    renderTable();
    expect(within(row(3)).getByText("이미 있음 — 건너뜀")).toBeInTheDocument();
    expect(within(row(3)).getByRole("checkbox", { name: "정보만 다시 가져오기" })).not.toBeChecked();
  });

  it("INVALID_URL → '주소 형식 오류'", () => {
    renderTable();
    expect(within(row(4)).getByText("주소 형식 오류")).toBeInTheDocument();
    expect(within(row(4)).queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("DUPLICATE → '중복 제거' + '(1번과 같음)'", () => {
    renderTable();
    expect(within(row(5)).getByText("중복 제거")).toBeInTheDocument();
    expect(row(5)).toHaveTextContent("(1번과 같음)");
  });

  it("주소 원문은 title 로 전체를 볼 수 있다", () => {
    renderTable();
    expect(row(4)).toHaveTextContent("https://example.com/foo");
    expect(within(row(1)).getByTitle(crawlCheckResponse.items[0].inputUrl)).toBeInTheDocument();
  });
});

describe("CrawlCheckTable — 정보만 다시 가져오기", () => {
  it("체크하면 onToggleRefresh(seq, true)", async () => {
    const user = userEvent.setup();
    const { onToggleRefresh } = renderTable();
    await user.click(within(row(3)).getByRole("checkbox", { name: "정보만 다시 가져오기" }));
    expect(onToggleRefresh).toHaveBeenCalledWith(3, true);
  });

  it("refreshSeqs 에 있으면 체크 상태 + 판정 '정보만 갱신' + 도움말", () => {
    renderTable({ refreshSeqs: [3] });
    expect(within(row(3)).getByRole("checkbox", { name: "정보만 다시 가져오기" })).toBeChecked();
    expect(within(row(3)).getByText("정보만 갱신")).toBeInTheDocument();
    expectText("이미 받은 파일은 다시 받지 않아요");
  });
});
