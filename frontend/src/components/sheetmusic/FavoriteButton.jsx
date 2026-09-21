/**
 * 즐겨찾기 버튼 — 00_공통 §3-17, 03_곡상세 §3-6. **곡 상세 곡 정보 영역에서만** 쓴다
 * (곡 카드·목록·관리 화면에는 두지 않는다 — 기획 05 §1-3·§5-8).
 *
 * - 상태는 `aria-pressed` 가 말한다. 보이는 글자가 곧 이름이라 `aria-label` 은 두지 않는다.
 * - 켜짐은 색만이 아니라 **글자(`즐겨찾기됨`)와 아이콘 채움**이 함께 바뀐다(00 §5).
 * - 응답 전 재클릭은 호출부가 무시하되 **비활성 모양으로 바꾸지 않는다**(03 §3-6-1 — 매번 회색이 번쩍이면 고장처럼 보인다).
 */
export function FavoriteButton({ favorited, onToggle }) {
  return (
    <button
      type="button"
      className={`btn btn-outline favorite-btn${favorited ? " favorite-btn-on" : ""}`}
      aria-pressed={favorited ? "true" : "false"}
      onClick={onToggle}
    >
      <span className="material-icons" aria-hidden="true">
        {favorited ? "star" : "star_border"}
      </span>
      {favorited ? "즐겨찾기됨" : "즐겨찾기"}
    </button>
  );
}
