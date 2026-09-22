/**
 * 화면 A-3 "이 판본으로 바꾸기" 패널 — **아직 비어 있다(TDD Red, senior-dev 2026-09-21).**
 *
 * 이 파일은 계약을 잡아 두기 위한 자리이고, 채우는 것은 frontend-dev 의 일이다.
 *   - 화면: `docs/화면정의/06_관리자_판본관리.md` A-3 (지금 추천 한 줄 · 경고 묶음 상자 · 사유 6개 · 메모 · 확인 체크 · 버튼)
 *   - 계약: `docs/설계/02_API_명세서.md` §5-6-2(열릴 때 경고 예고) · §5-6(바꾸기)
 *   - 실패해야 하는 테스트: `RecommendChangePanel.test.jsx`
 *
 * props (테스트가 고정한 계약):
 *   workId, edition(바꿀 판본), currentEdition(지금 추천 | null), currentReason(§4-7-2 current | null),
 *   onCancel(), onDone(§5-6 응답)
 */
export function RecommendChangePanel() {
  return null;
}
