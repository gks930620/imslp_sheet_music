import { screen } from "@testing-library/react";
import { vi } from "vitest";
import { SectionPreparingPage } from "./SectionPreparingPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { expectNoText, expectText } from "../../test/text.js";

// 화면정의 08 §2 준비 중 구분 안내 (신규 화면) · 기획 04 §1-3 · 인수 조건 8-A 4·5, 8-G 2
// 이 화면은 404 가 아니다 — 정상 화면이고, 서버에서 받아올 것이 없다.

function render(route) {
  return renderWithProviders(<SectionPreparingPage />, { route });
}

describe("SectionPreparingPage — 문구와 출구", () => {
  it("제목·설명이 그 구분 이름으로 보인다", () => {
    render("/violin");
    expectText("바이올린 악보는 아직 준비 중이에요");
    expectText("지금은 피아노 악보만 제공하고 있어요.");
    expectText("바이올린 악보는 준비되는 대로 이 자리에 열립니다.");
  });

  it("오케스트라도 같은 화면이고 구분 이름만 바뀐다", () => {
    render("/orchestra");
    expectText("오케스트라 악보는 아직 준비 중이에요");
    expectText("오케스트라 악보는 준비되는 대로 이 자리에 열립니다.");
  });

  it("출구 1: '피아노 악보 보러 가기' → 피아노 구분 홈", () => {
    render("/violin");
    expect(screen.getByRole("link", { name: "피아노 악보 보러 가기" })).toHaveAttribute("href", "/piano");
  });

  it("출구 2: IMSLP 로 새 탭 (rel=noreferrer)", () => {
    render("/violin");
    const imslp = screen.getByRole("link", { name: /IMSLP 에서 바이올린 악보 직접 찾아보기/ });
    expect(imslp).toHaveAttribute("target", "_blank");
    expect(imslp).toHaveAttribute("rel", expect.stringContaining("noreferrer"));
    expect(imslp.getAttribute("href")).toContain("imslp.org");
  });

  it("본문에 검색창을 두지 않는다 — 여기서 검색하면 0건밖에 안 나온다 (인수 조건 8-A 5)", () => {
    render("/violin");
    expect(screen.queryByRole("search")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("'열리면 알려주기' 같은 신청 자리가 없다 (인수 조건 8-G 2)", () => {
    render("/violin");
    expectNoText("알려");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("빈 목록·건수·'결과가 없어요' 를 어떤 형태로도 두지 않는다 (08 §2 상태표)", () => {
    render("/violin");
    expectNoText("결과가 없");
    expectNoText("0곡");
    expectNoText("찾을 수 없는 페이지");
  });
});

describe("SectionPreparingPage — 서버를 부르지 않는다", () => {
  it("로딩·에러 상태가 존재하지 않는다 (02 §7 — 호출 없음)", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render("/violin");
    expect(fetchMock).not.toHaveBeenCalled();
    expectText("바이올린 악보는 아직 준비 중이에요");
  });
});

describe("SectionPreparingPage — 검색어를 들고 왔을 때 (08 §2-3, 기획 04 §1-2)", () => {
  it("돌아가기 링크가 보이고, IMSLP 출구 문구도 그 검색어로 바뀐다", () => {
    render("/violin?q=녹턴");
    expectText("'녹턴' 검색 결과로 돌아가기");
    const imslp = screen.getByRole("link", { name: /IMSLP 에서 '녹턴' 찾아보기/ });
    expect(imslp.getAttribute("href")).toContain(encodeURIComponent("녹턴"));
  });

  it("검색어가 없으면 돌아가기 링크가 없다", () => {
    render("/violin");
    expectNoText("검색 결과로 돌아가기");
  });

  it("검색어가 길면 앞 15자 + … 로 줄인다 (08 §2-3)", () => {
    const long = "가나다라마바사아자차카타파하거너더";
    render(`/violin?q=${encodeURIComponent(long)}`);
    expectText(`'${long.slice(0, 15)}…' 검색 결과로 돌아가기`);
  });
});
