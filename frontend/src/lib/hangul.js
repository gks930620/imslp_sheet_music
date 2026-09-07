// 04_작곡가.md 화면 A — 작곡가 목록의 가나다 구분 헤더용 초성 계산.
const CHOSUNG = [
  "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ",
  "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ",
];

const HANGUL_BASE = 0xac00;
const HANGUL_LAST = 0xd7a3;
const JUNGSUNG_JONGSUNG_COUNT = 588;

/**
 * 한글 표기의 첫 글자 초성을 돌려준다. 한글이 아니면 첫 글자를 대문자로.
 * @param {string|null|undefined} value
 * @returns {string} 초성 1글자 / 알파벳 1글자 / 빈 문자열
 */
export function getChosung(value) {
  if (!value) return "";
  const first = value.trim().charAt(0);
  if (!first) return "";

  const code = first.charCodeAt(0);
  if (code >= HANGUL_BASE && code <= HANGUL_LAST) {
    return CHOSUNG[Math.floor((code - HANGUL_BASE) / JUNGSUNG_JONGSUNG_COUNT)];
  }
  return first.toUpperCase();
}
