import { getChosung } from "./hangul.js";

// 04_작곡가.md 화면 A: 가나다 구분 헤더 — 한글 표기 첫 글자의 초성, 한글이 아니면 첫 알파벳(대문자)
describe("getChosung — 작곡가 목록 구분 헤더용 초성", () => {
  it.each([
    ["그리그", "ㄱ"],
    ["까치", "ㄲ"],
    ["드뷔시", "ㄷ"],
    ["베토벤", "ㅂ"],
    ["쇼팽", "ㅅ"],
    ["하이든", "ㅎ"],
  ])("%s → %s", (input, expected) => {
    expect(getChosung(input)).toBe(expected);
  });

  it("한글이 아니면 첫 글자를 대문자 알파벳으로", () => {
    expect(getChosung("Satie, Erik")).toBe("S");
    expect(getChosung("satie")).toBe("S");
  });

  it("빈 값은 빈 문자열", () => {
    expect(getChosung("")).toBe("");
    expect(getChosung(null)).toBe("");
    expect(getChosung(undefined)).toBe("");
  });
});
