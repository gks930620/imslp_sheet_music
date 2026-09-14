import { screen, within } from "@testing-library/react";
import { SectionTabs } from "./SectionTabs.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";

// 화면정의 08 §1 구분 바 · 기획 04 §1·§3-4 · 03_기술결정 §21(주소 구조)
// 현재 구분의 근원은 **주소 하나**다 — prop 으로 받지 않는다(03 §21-6).

function tabs() {
  return within(screen.getByRole("navigation", { name: "악기 구분" }));
}

function render(route) {
  return renderWithProviders(<SectionTabs />, { route });
}

describe("SectionTabs — 무엇이 보이나", () => {
  it("피아노·바이올린·오케스트라 세 탭이 이 순서로 있다 (짧은 이름 — 08 §1-5 D4)", () => {
    render("/piano");
    const links = tabs().getAllByRole("link");
    expect(links.map((link) => link.textContent.replace(/\s+/g, " ").trim())).toEqual([
      "피아노",
      "바이올린 준비 중",
      "오케스트라 준비 중",
    ]);
  });

  it("준비 중 구분은 누르기 전에도 '준비 중'이 글자로 보인다 (인수 조건 8-A 2)", () => {
    render("/piano");
    expect(tabs().getByRole("link", { name: /바이올린/ })).toHaveTextContent("준비 중");
    expect(tabs().getByRole("link", { name: /오케스트라/ })).toHaveTextContent("준비 중");
    expect(tabs().getByRole("link", { name: /^피아노/ })).not.toHaveTextContent("준비 중");
  });

  it("화면 어디에도 '악기 구분'이라는 말을 글자로 쓰지 않는다 (기획 04 §0-1 — aria-label 은 예외)", () => {
    render("/piano");
    expect(screen.getByRole("navigation", { name: "악기 구분" }).textContent).not.toContain("악기 구분");
  });
});

describe("SectionTabs — 필터가 아니라 화면 이동이다 (08 §1-2)", () => {
  it("세 탭 모두 href 가 붙은 진짜 링크다 — 우클릭 새 탭·링크 복사가 동작해야 한다 (08 §7 F1)", () => {
    render("/piano");
    expect(tabs().getByRole("link", { name: /^피아노/ })).toHaveAttribute("href", "/piano");
    expect(tabs().getByRole("link", { name: /바이올린/ })).toHaveAttribute("href", "/violin");
    expect(tabs().getByRole("link", { name: /오케스트라/ })).toHaveAttribute("href", "/orchestra");
  });

  it("피아노 탭은 검색 결과에서도 그 구분의 '홈'으로 간다 (기획 04 §1-1 3번 — 목록만 바뀌는 스위치가 아니다)", () => {
    render("/piano/search?q=녹턴&level=INTERMEDIATE&page=2");
    expect(tabs().getByRole("link", { name: /^피아노/ })).toHaveAttribute("href", "/piano");
  });

  it("role=\"tab\"/\"tablist\" 를 쓰지 않는다 — 그 역할은 '같은 화면에서 패널만 바꾼다'는 뜻이다 (08 §7 F2)", () => {
    render("/piano");
    expect(screen.queryAllByRole("tab")).toHaveLength(0);
    expect(screen.queryAllByRole("tablist")).toHaveLength(0);
  });
});

// 준비 중 탭은 검색 결과 화면에서 누르면 검색 상태를 그 주소에 실어 간다 —
// 그래야 준비 중 안내 화면(SectionPreparingPage)이 그 쿼리로 "돌아가기" 링크를 만든다(08 §2-3).
// 기획 04 인수 조건 8-A 8(검색에서 준비 중 탭 → 검색어 든 돌아가기 링크)·8-A 9(검색 밖에서는 없음).
// 실어 가는 건 검색 상태 6개(q·in·level·pages·downloadable·page)뿐이고, `in=ALL` 은 기본값이라 생략된다.
// 주소의 값은 있는 그대로 옮긴다(재가공하지 않는다) — `in` 은 대문자 enum 표기(searchScope.js·02 §3-1).
function href(link) {
  return new URL(link.getAttribute("href"), "http://x");
}

describe("SectionTabs — 준비 중 탭이 검색 상태를 실어 간다 (08 §2-3, 인수 조건 8-A 8·9)", () => {
  it("검색 결과 화면에서 준비 중 탭은 검색 상태(q·in·level·pages·downloadable·page)를 그 주소에 싣는다", () => {
    render("/piano/search?q=녹턴&in=TITLE&level=ELEMENTARY&pages=LE10&downloadable=true&page=1");
    for (const name of [/바이올린/, /오케스트라/]) {
      const url = href(tabs().getByRole("link", { name }));
      expect(url.pathname).toBe(name.source.includes("바이올린") ? "/violin" : "/orchestra");
      expect(url.searchParams.get("q")).toBe("녹턴");
      expect(url.searchParams.get("in")).toBe("TITLE");
      expect(url.searchParams.get("level")).toBe("ELEMENTARY");
      expect(url.searchParams.get("pages")).toBe("LE10");
      expect(url.searchParams.get("downloadable")).toBe("true");
      expect(url.searchParams.get("page")).toBe("1");
    }
  });

  it("피아노(현재 열린) 탭은 검색 상태를 버리고 그 구분의 홈으로 간다 (기획 04 §1-1 5번·8-B 7)", () => {
    render("/piano/search?q=녹턴&level=INTERMEDIATE&page=2");
    expect(tabs().getByRole("link", { name: /^피아노/ })).toHaveAttribute("href", "/piano");
  });

  it("검색 결과 화면이 아니면 준비 중 탭에 쿼리를 붙이지 않는다 — 홈 (8-A 9 ①)", () => {
    render("/piano");
    expect(tabs().getByRole("link", { name: /바이올린/ })).toHaveAttribute("href", "/violin");
  });

  it("검색 결과 화면이 아니면 준비 중 탭에 쿼리를 붙이지 않는다 — 곡 상세 (8-A 9 ②)", () => {
    render("/piano/works/21");
    expect(tabs().getByRole("link", { name: /바이올린/ })).toHaveAttribute("href", "/violin");
  });

  it("작곡가 상세의 필터 쿼리(level·page)는 검색 상태가 아니므로 준비 중 탭에 실리지 않는다 (8-A 9 ②)", () => {
    render("/piano/composers/9?level=ADVANCED&page=2");
    expect(tabs().getByRole("link", { name: /바이올린/ })).toHaveAttribute("href", "/violin");
  });

  it("화면 전용 쿼리 from 은 검색 상태가 아니라 다음 구분으로 넘기지 않는다 (08 §2-4)", () => {
    render("/piano/search?q=녹턴&from=violin");
    const url = href(tabs().getByRole("link", { name: /오케스트라/ }));
    expect(url.searchParams.get("q")).toBe("녹턴");
    expect(url.searchParams.has("from")).toBe(false);
  });
});

describe("SectionTabs — 지금 어느 구분인가 (기획 04 §1-4)", () => {
  it.each([
    ["/piano", "피아노"],
    ["/piano/search?q=녹턴", "피아노"],
    ["/piano/works/21", "피아노"],
    ["/piano/composers/9", "피아노"],
    ["/violin", "바이올린"],
    ["/orchestra", "오케스트라"],
  ])("%s 에서는 '%s' 탭에만 aria-current=\"page\" 가 붙는다", (route, expected) => {
    render(route);
    const current = tabs()
      .getAllByRole("link")
      .filter((link) => link.getAttribute("aria-current") === "page");
    expect(current).toHaveLength(1);
    expect(current[0]).toHaveTextContent(expected);
  });

  it.each(["/cello", "/cello/works/3", "/admin/works"])(
    "%s 처럼 구분이 아닌 주소에서는 선택된 탭이 하나도 없다 (08 §3 · 인수 조건 8-B 6)",
    (route) => {
      render(route);
      const current = tabs()
        .getAllByRole("link")
        .filter((link) => link.getAttribute("aria-current") === "page");
      expect(current).toHaveLength(0);
    },
  );

  it("주소의 구분 문자열을 화면에 되뱉지 않는다 (08 §3)", () => {
    render("/첼로<script>");
    expect(document.body.textContent).not.toContain("첼로");
  });
});
