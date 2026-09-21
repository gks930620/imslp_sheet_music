import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  LOGIN_INTENT_KEY,
  LOGIN_INTENT_TTL_MS,
  clearLoginIntent,
  loginHref,
  peekLoginIntent,
  saveLoginIntent,
  takeLoginIntent,
} from "./loginIntent.js";

/**
 * 로그인 왕복 너머로 "이 곡을 즐겨찾기하려 했다" 를 들고 가는 수단 — 03_기술결정 §22, 02 §10-4.
 * 기획 05 §1-2, 인수 조건 8-B 3·4·6.
 *
 * <b>저장소가 sessionStorage 인 것이 계약이다</b>: 카카오·구글은 우리 오리진을 떠났다가 새 문서로
 * 돌아오므로 주소의 `?redirect=` 가 사라지고(OAuth2LoginSuccessHandler 가 "/" 로 보낸다), 그래도
 * 같은 탭의 sessionStorage 는 남는다. localStorage 는 공용 컴퓨터에서 다른 사람의 탭에 되살아난다.
 */
describe("loginIntent — 로그인 왕복 너머로 의도를 들고 간다", () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it("저장하면 sessionStorage 한 칸에 들어간다 (localStorage 가 아니다)", () => {
    saveLoginIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });

    const stored = JSON.parse(sessionStorage.getItem(LOGIN_INTENT_KEY));
    expect(stored).toMatchObject({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });
    expect(typeof stored.at).toBe("number");
    expect(localStorage.getItem(LOGIN_INTENT_KEY)).toBeNull();
  });

  it("take 는 읽는 즉시 지운다 — 두 번째 take 는 null (새로고침에 두 번 실행되지 않는 근거, 8-B 4)", () => {
    saveLoginIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });

    expect(takeLoginIntent()).toMatchObject({ action: "FAVORITE", workId: 21 });
    expect(takeLoginIntent()).toBeNull();
    expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).toBeNull();
  });

  it("peek 은 지우지 않는다 — 주소 점프(§22-2)는 보기만 하고 소비는 곡 상세가 한다", () => {
    saveLoginIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });

    expect(peekLoginIntent()?.returnTo).toBe("/piano/works/21");
    expect(peekLoginIntent()?.returnTo).toBe("/piano/works/21");
  });

  it("10분이 지난 의도는 소비되지 않고 버려진다", () => {
    vi.useFakeTimers();
    saveLoginIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });

    vi.advanceTimersByTime(LOGIN_INTENT_TTL_MS + 1000);
    expect(peekLoginIntent()).toBeNull();
    expect(takeLoginIntent()).toBeNull();
    expect(sessionStorage.getItem(LOGIN_INTENT_KEY)).toBeNull();
    vi.useRealTimers();
  });

  it("TTL 은 10분이다", () => {
    expect(LOGIN_INTENT_TTL_MS).toBe(10 * 60 * 1000);
  });

  it("clear 로 버릴 수 있다 — 비로그인으로 그 곡에 돌아온 경우(뒤로 가기, 8-B 6)", () => {
    saveLoginIntent({ action: "FAVORITE", workId: 21, returnTo: "/piano/works/21" });

    clearLoginIntent();
    expect(peekLoginIntent()).toBeNull();
  });

  it("값이 깨져 있으면 null 이다 — 오류를 던지지 않는다", () => {
    sessionStorage.setItem(LOGIN_INTENT_KEY, "{망가진 값");

    expect(peekLoginIntent()).toBeNull();
    expect(takeLoginIntent()).toBeNull();
  });

  it("내 악보 진입처럼 곡이 없는 의도도 담는다 (action 없음, returnTo 만)", () => {
    saveLoginIntent({ returnTo: "/piano/library/downloads" });

    expect(peekLoginIntent()).toMatchObject({ action: null, workId: null, returnTo: "/piano/library/downloads" });
  });

  it("loginHref — 돌아갈 주소는 인코딩하고 이유는 소문자 값이다 (02 §10-4)", () => {
    expect(loginHref("/piano/works/21", "favorite")).toBe("/login?redirect=%2Fpiano%2Fworks%2F21&reason=favorite");
    expect(loginHref("/piano/library/favorites", "library")).toBe(
      "/login?redirect=%2Fpiano%2Flibrary%2Ffavorites&reason=library",
    );
    expect(loginHref("/piano/library/downloads?page=1", "library")).toBe(
      "/login?redirect=%2Fpiano%2Flibrary%2Fdownloads%3Fpage%3D1&reason=library",
    );
  });

  it("이유가 없으면 reason 을 붙이지 않는다 — 기존 복귀(관리 딥링크)와 같은 주소가 된다", () => {
    expect(loginHref("/admin/works")).toBe("/login?redirect=%2Fadmin%2Fworks");
  });
});
