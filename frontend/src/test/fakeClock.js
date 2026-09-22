import { act } from "@testing-library/react";
import { vi } from "vitest";
import { bodyText, expectText } from "./text.js";

/**
 * 가짜 시계(`vi.useFakeTimers()`) 위에서 화면을 관찰하는 도구.
 * "응답이 오지 않아도 10초 뒤 버튼이 풀린다"(03_기술결정 §28-1 규칙 5·7) 처럼 **긴 타이머를 건너뛰어야 하는**
 * 화면 테스트에서만 쓴다. 보통의 비동기 대기는 `findText`(text.js) 그대로다.
 *
 * ### 왜 따로 필요한가 — 순서가 전부다 (2026-09-21 senior-dev, 실측)
 *
 * 가짜 시계는 **설치 이후에 잡힌 타이머만** 가로챈다. 설치 전에 잡힌 `setTimeout` 은 `vi.getTimerCount()` 에
 * 보이지도 않고 `advanceTimersByTimeAsync()` 로 발화하지도 않는다(진짜 시계에 그대로 남는다).
 * 10초 타이머는 **클릭 시점**에 잡히므로, 시계를 클릭 뒤에 깔면 그 타이머는 영영 관찰할 수 없다.
 * → 시계는 **렌더·클릭보다 먼저** 깔아야 한다.
 *
 * 그런데 먼저 깔면 `waitFor`(= `findText`) 의 폴링·타임아웃 타이머까지 가짜가 돼 영원히 기다린다.
 * RTL 은 vitest 의 가짜 시계를 감지하지 못한다 — 감지 코드가 `jest` 전역을 보는데 이 저장소에는 없다.
 * 그래서 "기다림" 을 직접 만든다: 시계를 조금씩 돌리며 그 사이의 React 갱신을 `act` 로 흘려보낸다.
 */

/** 가짜 시계를 `ms` 만큼 돌리고 그 사이에 밀린 React 갱신까지 흘려보낸다. `0` 이면 "지금 밀린 것만" 흘린다 */
export async function tick(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** 가짜 시계 위의 `findText` — 문구가 나타날 때까지 0ms 씩 돌린다. 끝내 없으면 `expectText` 로 실패한다 */
export async function findTextOnFakeClock(text, tries = 20) {
  for (let i = 0; i < tries; i += 1) {
    await tick();
    if (bodyText().includes(text)) break;
  }
  expectText(text);
}
