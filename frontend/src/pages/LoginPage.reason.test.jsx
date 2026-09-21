import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LoginPage } from "./LoginPage.jsx";
import { renderWithProviders } from "../test/renderWithProviders.jsx";
import { expectNoText, expectText } from "../test/text.js";

/**
 * 로그인 화면의 "이유 한 줄" — 화면정의 09 §3, 기획 05 §1-2 1 · §2-1 5, 인수 조건 8-B 2 · 8-C 8.
 * 계약: 02 §10-4 — 화면 전용 쿼리 `reason`, 값 `favorite` | `library`. 그 밖의 값은 **없음과 같다**.
 *
 * 로그인 화면은 이번에 정비하지 않는다(기획 05 §0-3) — <b>바뀌는 것은 부제 한 줄뿐</b>이고,
 * 배너·InlineAlert 로 키우지 않는다(8-G 2 "권유는 로그인 화면 안의 한 줄뿐").
 */

function renderLogin(route) {
  return renderWithProviders(<LoginPage />, { route, path: "/login", auth: "guest" });
}

describe("LoginPage — 이유 한 줄 2종", () => {
  it("즐겨찾기에서 왔으면 '즐겨찾기는 로그인하면 쓸 수 있어요' 가 기존 부제를 대신한다", () => {
    renderLogin("/login?redirect=%2Fpiano%2Fworks%2F21&reason=favorite");

    expectText("즐겨찾기는 로그인하면 쓸 수 있어요");
    expectNoText("서비스에 로그인하세요");
  });

  it("내 악보에서 왔으면 '내 악보는 로그인하면 볼 수 있어요'", () => {
    renderLogin("/login?redirect=%2Fpiano%2Flibrary%2Ffavorites&reason=library");

    expectText("내 악보는 로그인하면 볼 수 있어요");
    expectNoText("서비스에 로그인하세요");
  });

  it("이유가 없으면 기존 부제 그대로 — 헤더 '로그인'·관리 딥링크는 달라지지 않는다", () => {
    renderLogin("/login?redirect=%2Fadmin%2Fworks");

    expectText("서비스에 로그인하세요");
    expectNoText("로그인하면");
  });

  it("알 수 없는 이유는 '없음' 과 같다 — 오류가 아니다 (09 §3)", () => {
    renderLogin("/login?reason=xyz");

    expectText("서비스에 로그인하세요");
  });

  it("배너·경고 상자로 만들지 않는다 — 한 줄뿐이다 (8-G 2)", () => {
    renderLogin("/login?reason=favorite");

    expect(document.querySelector(".inline-alert")).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("'이전 페이지로 돌아가기' 는 돌아갈 주소 그대로다 — 즐겨찾기는 되지 않는다 (8-B 6)", () => {
    renderLogin("/login?redirect=%2Fpiano%2Fworks%2F21&reason=favorite");

    expect(screen.getByRole("link", { name: "이전 페이지로 돌아가기" })).toHaveAttribute("href", "/piano/works/21");
  });
});
