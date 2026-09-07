import { useAuth } from "../context/AuthContext.jsx";

export function MyPage() {
  const { user } = useAuth();

  return (
    <div className="mypage-container">
      <div className="mypage-header">
        <h1>마이페이지</h1>
      </div>
      <dl className="user-info-section">
        <dt>아이디 (Username)</dt>
        <dd>{user?.username ?? "정보 없음"}</dd>
        <dt>닉네임</dt>
        <dd>{user?.nickname ?? "정보 없음"}</dd>
        <dt>이메일</dt>
        <dd>{user?.email ?? "정보 없음"}</dd>
        <dt>로그인 제공자</dt>
        <dd>{user?.provider ?? "정보 없음"}</dd>
      </dl>
    </div>
  );
}
