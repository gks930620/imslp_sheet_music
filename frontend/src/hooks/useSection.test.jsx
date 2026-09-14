import { cleanup } from "@testing-library/react";
import { useSection, useSectionPath } from "./useSection.js";
import { renderWithProviders } from "../test/renderWithProviders.jsx";

// frontend-dev 내부 단위 — 03 §21-6: 현재 구분의 유일한 근원은 주소다. prop 으로 내려보내지 않는다.

function Probe() {
  const section = useSection();
  const path = useSectionPath();
  return (
    <div>
      <span data-testid="slug">{section ? section.slug : "null"}</span>
      <span data-testid="home">{path()}</span>
      <span data-testid="works">{path("/works/21")}</span>
    </div>
  );
}

function read(route) {
  cleanup(); // 한 테스트 안에서 여러 주소를 읽는다
  const { getByTestId } = renderWithProviders(<Probe />, { route });
  return {
    slug: getByTestId("slug").textContent,
    home: getByTestId("home").textContent,
    works: getByTestId("works").textContent,
  };
}

describe("useSection — 경로 첫 세그먼트", () => {
  it.each([
    ["/piano", "piano"],
    ["/piano/search?q=녹턴", "piano"],
    ["/violin", "violin"],
    ["/orchestra?q=x", "orchestra"],
  ])("%s → %s", (route, slug) => {
    expect(read(route).slug).toBe(slug);
  });

  it.each(["/", "/admin/works", "/cello/works/3", "/login"])("%s → null (구분 밖)", (route) => {
    expect(read(route).slug).toBe("null");
  });
});

describe("useSectionPath — 구분 안 링크 접두사 (02 §0-5)", () => {
  it("열린 구분에서는 그 구분으로", () => {
    expect(read("/piano/works/3")).toMatchObject({ home: "/piano", works: "/piano/works/21" });
  });

  it("구분 밖·준비 중에서는 기본 구분(piano)으로", () => {
    expect(read("/admin/works").home).toBe("/piano");
    expect(read("/cello").works).toBe("/piano/works/21");
    expect(read("/violin").works).toBe("/piano/works/21");
  });
});
