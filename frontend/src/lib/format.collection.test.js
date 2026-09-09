import { isCollectionWork } from "./format.js";

// 02 §2-2-1 "COLLECTION 의 두 번째 쓰임" — 코드 문자열 "COLLECTION" 의 단일 출처.
// WorkCard 의 '(전곡 기준)' 판정과 formatWorkScopeLine 의 별칭 문구 판정이 같은 함수를 쓴다.

describe("isCollectionWork (scopeNote 가 묶음 악보인지)", () => {
  it("codes 에 COLLECTION 이 있으면 true", () => {
    expect(isCollectionWork({ codes: ["COLLECTION"], movementNumber: null })).toBe(true);
  });

  it("다른 코드와 함께 있어도 true", () => {
    expect(isCollectionWork({ codes: ["ARRANGEMENT", "COLLECTION"], movementNumber: null })).toBe(true);
  });

  it("COLLECTION 이 없으면 false", () => {
    expect(isCollectionWork({ codes: ["ARRANGEMENT", "MOVEMENT_ONLY"], movementNumber: 2 })).toBe(false);
  });

  it("scopeNote 가 null·undefined 면 false (시스템은 묶음인지 모른다)", () => {
    expect(isCollectionWork(null)).toBe(false);
    expect(isCollectionWork(undefined)).toBe(false);
  });

  it("codes 가 없어도 터지지 않는다", () => {
    expect(isCollectionWork({})).toBe(false);
  });
});
