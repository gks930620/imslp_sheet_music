import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TOAST_UNDO_MS, dismissToast, showToast } from "./Toast.jsx";
import { bodyText } from "../../test/text.js";

/**
 * Toast 의 성공·실패 아이콘과 수동 닫기 — 화면정의 00 §3-11, 03_기술결정 §25-1 → §29.
 *
 * 정의서는 처음부터 셋을 요구했다: 성공 `check_circle` · 실패 `error` · 수동 닫기 `close`.
 * 세 가지가 다 없어서 §25-1 이 "Toast 를 손대는 다음 작업" 으로 미뤄 둔 것을 여기서 계약으로 잠근다.
 *
 * 이 파일이 지키는 어려운 것 셋
 *   ⑴ **호출부 10여 곳을 한 글자도 고치지 않는다** — `showToast("저장했어요")` 가 기본값으로 성공 아이콘을 얻는다
 *   ⑵ 닫기 버튼의 접근 이름은 `알림 닫기` 다 — 라이트박스의 `닫기`(WorkDetailPage.test.jsx)와 조회가 겹치면 안 된다
 *   ⑶ 되돌리기 변형은 **아이콘이 없다**(00 §3-11 표: "아이콘 없음(중립 알림)") — 닫기는 그대로 있다
 */

afterEach(() => dismissToast());

/** 한 줄의 구성 요소를 왼쪽부터 이름으로 읽는다 — 클래스 전체 문자열에 매달리지 않는다 */
function slots() {
  const toast = screen.getByRole("status");
  return Array.from(toast.children).map((el) => {
    if (el.classList.contains("toast-icon")) return "icon";
    if (el.classList.contains("toast-message")) return "message";
    if (el.classList.contains("toast-action")) return "action";
    if (el.classList.contains("toast-close")) return "close";
    return el.className || el.tagName.toLowerCase();
  });
}

function icon() {
  return document.querySelector(".toast-icon");
}

function closeButton() {
  return screen.queryByRole("button", { name: "알림 닫기" });
}

describe("Toast 아이콘 (00 §3-11)", () => {
  it("기본은 성공이다 — 기존 호출부 showToast('저장했어요') 가 그대로 check_circle 을 얻는다", () => {
    showToast("저장했어요");

    expect(icon()).toHaveTextContent("check_circle");
    expect(icon()).toHaveClass("material-icons");
    expect(screen.getByRole("status")).toHaveClass("toast-success");
    expect(bodyText()).toContain("저장했어요"); // 아이콘이 붙어도 문구 단언은 그대로 성립한다
  });

  it("실패는 error 아이콘이다", () => {
    showToast("즐겨찾기를 저장하지 못했어요. 다시 시도해 주세요", { variant: "danger" });

    expect(icon()).toHaveTextContent("error");
    expect(screen.getByRole("status")).toHaveClass("toast-danger");
  });

  it("아이콘은 읽히지 않는다 — material-icons 의 글자(check_circle)가 낭독에 섞이면 안 된다", () => {
    showToast("저장했어요");

    expect(icon()).toHaveAttribute("aria-hidden", "true");
  });

  it("한 줄의 차례는 아이콘 → 문구 → 닫기다", () => {
    showToast("삭제했어요");

    expect(slots()).toEqual(["icon", "message", "close"]);
  });
});

describe("Toast 수동 닫기 (00 §3-11)", () => {
  it("닫기 버튼의 접근 이름은 '알림 닫기' 다 — '닫기' 로는 잡히지 않는다", () => {
    showToast("저장했어요");

    expect(closeButton()).toBeInTheDocument();
    // 03 라이트박스의 닫기 버튼과 조회가 겹치면 두 화면의 테스트가 서로를 잡는다(§25-1 주의)
    expect(screen.queryByRole("button", { name: "닫기" })).toBeNull();
  });

  it("누르면 그 자리에서 사라진다", () => {
    showToast("저장했어요");

    closeButton().click();

    expect(screen.queryByRole("status")).toBeNull();
    expect(bodyText()).not.toContain("저장했어요");
  });

  it("닫은 뒤에는 자동 사라짐 타이머도 남지 않는다", () => {
    vi.useFakeTimers();
    showToast("저장했어요");
    expect(vi.getTimerCount()).toBe(1);

    closeButton().click();

    expect(vi.getTimerCount()).toBe(0);
  });
});

describe("Toast 되돌리기 변형과의 조합 (00 §3-11 되돌리기 표)", () => {
  const undo = (onClick = () => {}) => ({
    durationMs: TOAST_UNDO_MS,
    action: { label: "되돌리기", onClick },
  });

  it("되돌리기 변형에는 아이콘이 없다 — 중립 알림이다", () => {
    showToast("즐겨찾기에서 뺐어요", undo());

    expect(icon()).toBeNull();
    expect(screen.getByRole("button", { name: "되돌리기" })).toBeInTheDocument();
  });

  it("되돌리기가 있으면 variant 를 줘도 아이콘은 붙지 않는다 — 폭 480px 한 줄에 넷은 들어가지 않는다", () => {
    showToast("즐겨찾기에서 뺐어요", { ...undo(), variant: "success" });

    expect(icon()).toBeNull();
  });

  it("되돌리기 변형에도 닫기는 있다 — 차례는 문구 → 되돌리기 → 닫기다", () => {
    showToast("즐겨찾기에서 뺐어요", undo());

    expect(slots()).toEqual(["message", "action", "close"]);
  });

  it("닫기로 내리면 되돌리기는 실행되지 않는다 — 그 행동은 그대로 확정된다", () => {
    const onClick = vi.fn();
    showToast("즐겨찾기에서 뺐어요", undo(onClick));

    closeButton().click();

    expect(onClick).not.toHaveBeenCalled();
    expect(screen.queryByRole("status")).toBeNull();
  });
});
