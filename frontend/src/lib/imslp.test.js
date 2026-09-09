import { imslpSearchUrl } from "./imslp.js";

/**
 * 검색 0건에서 내보내는 IMSLP 검색 주소 — 기획 §5 예외표(0건) · §10-7
 * (frontend-dev 작성. 주소를 만드는 규칙은 내부 구현 세부라 여기서 먼저 못을 박는다).
 *
 * <p>0건이 "세상에 없어요" 가 아니라 "우리가 일부러 뺐어요" 인 경우가 많다(캐논·사랑의 인사 등).
 * 그때 사용자를 빈손으로 내보내지 않으려면 <b>그 검색어 그대로</b> IMSLP 검색 결과로 보내야 한다.
 */
describe("imslpSearchUrl — 검색어를 실은 IMSLP 검색 주소", () => {
  it("검색어를 퍼센트 인코딩해 IMSLP 검색 주소를 만든다", () => {
    expect(imslpSearchUrl("캐논")).toBe(
      "https://imslp.org/index.php?title=Special:Search&search=%EC%BA%90%EB%85%BC",
    );
  });

  it("공백·마침표가 섞인 원어 검색어도 안전하게 싣는다", () => {
    expect(imslpSearchUrl("Op.27 No.2")).toBe(
      "https://imslp.org/index.php?title=Special:Search&search=Op.27%20No.2",
    );
  });

  it("앞뒤 공백은 떼고 싣는다", () => {
    expect(imslpSearchUrl("  canon  ")).toBe("https://imslp.org/index.php?title=Special:Search&search=canon");
  });

  it("검색어가 없으면 주소를 만들지 않는다 — 보낼 곳 없는 링크는 만들지 않는다", () => {
    expect(imslpSearchUrl("")).toBeNull();
    expect(imslpSearchUrl("   ")).toBeNull();
    expect(imslpSearchUrl(null)).toBeNull();
    expect(imslpSearchUrl(undefined)).toBeNull();
  });
});
