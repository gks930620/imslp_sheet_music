import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { callApi } from "../lib/http.js";
import { getErrorMessage } from "../lib/format.js";

export function RoomListPage() {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState([]);
  const [newRoomName, setNewRoomName] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");
  const [authRequired, setAuthRequired] = useState(false);

  const loadRooms = async () => {
    setLoading(true);
    setError("");
    setAuthRequired(false);
    try {
      const result = await callApi("/api/rooms");
      setRooms(result.data ?? []);
    } catch (e) {
      if (e?.status === 401) {
        setAuthRequired(true);
      }
      setError(getErrorMessage(e, "채팅방 목록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRooms();
  }, []);

  const onCreate = async (event) => {
    event.preventDefault();
    if (!newRoomName.trim()) return;

    setCreating(true);
    try {
      const result = await callApi("/api/rooms", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: newRoomName.trim() }),
      });
      setNewRoomName("");
      navigate(`/rooms/${result.data.id}`);
    } catch (e) {
      alert(getErrorMessage(e, "채팅방 생성에 실패했습니다."));
    } finally {
      setCreating(false);
    }
  };

  return (
    <section>
      <div className="rooms-container">
        <div className="rooms-title">
          <span className="material-icons">chat</span>
          <span>채팅방 목록</span>
        </div>

        {!loading && authRequired && (
          <div className="login-required">
            <span className="material-icons">lock</span>
            <p>채팅은 로그인 후 이용 가능합니다.</p>
            <Link to="/login">
              <span className="material-icons">login</span>
              <span>로그인하기</span>
            </Link>
          </div>
        )}

        {!authRequired && (
          <form className="room-create" onSubmit={onCreate}>
            <input
              className="room-create-input"
              maxLength={50}
              placeholder="새 채팅방 이름"
              required
              type="text"
              value={newRoomName}
              onChange={(e) => setNewRoomName(e.target.value)}
            />
            <button className="room-create-btn" disabled={creating} type="submit">
              {creating ? "생성 중..." : "채팅방 만들기"}
            </button>
          </form>
        )}

        {loading && <div className="empty-message">로딩 중...</div>}
        {!loading && !authRequired && error && <div className="empty-message">{error}</div>}
        {!loading && !authRequired && !error && rooms.length === 0 && (
          <div className="empty-message">
            <span className="material-icons" style={{ fontSize: 48, color: "#ccc" }}>
              forum
            </span>
            <p>아직 채팅방이 없습니다.</p>
          </div>
        )}
        {!loading && !authRequired && !error && rooms.length > 0 && (
          <ul className="room-list">
            {rooms.map((room) => (
              <li key={room.id}>
                <Link className="room-item" to={`/rooms/${room.id}`}>
                  <div className="room-info">
                    <div className="room-icon">
                      <span className="material-icons">forum</span>
                    </div>
                    <div>
                      <div className="room-name">{room.name}</div>
                      <div className="room-id">#{room.id}</div>
                    </div>
                  </div>
                  <div className="room-enter">
                    <span className="material-icons">arrow_forward</span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
