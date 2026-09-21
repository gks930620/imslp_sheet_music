import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { PageTabs } from "./PageTabs.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";

/**
 * 공용 본문 탭 — 00_공통 §3-18. 내부 구현 세부라 frontend-dev 가 직접 먼저 작성한 테스트다
 * (화면 쪽 계약은 senior-dev 의 `MyLibraryPage.test.jsx` 가 잠근다).
 */
function renderTabs({ current = "favorites", counts = {} } = {}) {
  return renderWithProviders(
    <PageTabs
      label="내 악보"
      current={current}
      tabs={[
        { key: "favorites", label: "즐겨찾기", href: "/piano/library/favorites", count: counts.favorites },
        { key: "downloads", label: "받은 악보", href: "/piano/library/downloads", count: counts.downloads },
      ]}
    />,
    { route: "/piano/library/favorites" },
  );
}

beforeEach(() => {
  window.scrollTo.mockClear();
});

describe("PageTabs", () => {
  it("탭은 진짜 링크이고 선택된 탭만 aria-current 다 — role=tab 을 쓰지 않는다 (09 F2)", () => {
    renderTabs({ counts: { favorites: 12, downloads: 5 } });

    expect(screen.getByRole("navigation", { name: "내 악보" })).toBeInTheDocument();
    expect(screen.queryAllByRole("tab")).toHaveLength(0);
    expect(screen.getByRole("link", { name: "즐겨찾기 (12)" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "받은 악보 (5)" })).not.toHaveAttribute("aria-current");
  });

  it("숫자를 모르는 동안은 괄호째 생략한다 — (0) 을 잠깐 보이지 않는다 (09 F6)", () => {
    renderTabs();

    expect(screen.getByRole("link", { name: "즐겨찾기" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "받은 악보" })).toBeInTheDocument();
  });

  it("0 은 숫자다 — 빈 탭에도 (0) 이 보인다 (09 §1-3)", () => {
    renderTabs({ counts: { favorites: 0, downloads: 0 } });

    expect(screen.getByRole("link", { name: "즐겨찾기 (0)" })).toBeInTheDocument();
  });

  it("탭을 누르면 스크롤이 맨 위로 간다 (09 상태표 '탭 전환')", async () => {
    const user = userEvent.setup();
    renderTabs({ counts: { favorites: 12, downloads: 5 } });

    await user.click(screen.getByRole("link", { name: "받은 악보 (5)" }));

    expect(window.scrollTo).toHaveBeenCalledWith(0, 0);
  });
});
