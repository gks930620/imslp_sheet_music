/**
 * 화면 A-1 "이 판본을 고른 이유" — **아직 비어 있다(TDD Red, senior-dev 2026-09-21).**
 *
 * 이 파일은 계약을 잡아 두기 위한 자리이고, 채우는 것은 frontend-dev 의 일이다.
 * 무엇을 그려야 하는지는 두 곳에 전부 적혀 있다:
 *   - 화면: `docs/화면정의/06_관리자_판본관리.md` A-1 (자동 / 사람 / 기록 없음 · 미검수 줄 · 바뀐 이력)
 *   - 계약: `docs/설계/02_API_명세서.md` §4-7-2(recommendation) · §4-7-1(더 보기) · §5-6-1(확인함)
 *   - 실패해야 하는 테스트: `RecommendationReasonBox.test.jsx`
 *
 * props (테스트가 고정한 계약):
 *   workId, hasRecommendation, reviewed, recommendation(§4-7-2 | null=불러오지 못함),
 *   recommendedEdition(미리보기용 | null), onChanged, onToast
 */
export function RecommendationReasonBox() {
  return null;
}
