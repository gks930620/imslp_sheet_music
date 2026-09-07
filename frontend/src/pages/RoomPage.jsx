import { useEffect, useMemo, useRef, useState } from "react";
import SockJS from "sockjs-client/dist/sockjs";
import { Client } from "@stomp/stompjs";
import { Link, useParams } from "react-router-dom";
import { callApi } from "../lib/http.js";
import { useAuth } from "../context/AuthContext.jsx";
import { getErrorMessage } from "../lib/format.js";

function parseMessage(body) {
  try {
    return JSON.parse(body);
  } catch {
    return { content: body };
  }
}

export function RoomPage() {
  const { roomId } = useParams();
  const { user } = useAuth();
  const clientRef = useRef(null);
  const messagesRef = useRef(null);
  const [roomName, setRoomName] = useState(`Room #${roomId}`);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    let mounted = true;

    callApi(`/api/rooms/${roomId}`)
      .then((result) => {
        if (mounted) {
          setRoomName(result.data?.name ?? `Room #${roomId}`);
        }
      })
      .catch((e) => {
        if (mounted) {
          setMessages((prev) => [...prev, { system: true, content: getErrorMessage(e, "방 정보를 불러오지 못했습니다.") }]);
        }
      });

    return () => {
      mounted = false;
    };
  }, [roomId]);

  useEffect(() => {
    if (!user?.username) return undefined;

    const client = new Client({
      webSocketFactory: () => new SockJS("/ws-chat"),
      reconnectDelay: 0,
      connectHeaders: {
        roomId: String(roomId),
      },
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/sub/room/${roomId}`, (frame) => {
          const parsed = parseMessage(frame.body);
          setMessages((prev) => [...prev, parsed]);
        });
      },
      onStompError: () => {
        setMessages((prev) => [...prev, { system: true, content: "채팅 연결에 실패했습니다." }]);
      },
      onWebSocketError: () => {
        setMessages((prev) => [...prev, { system: true, content: "웹소켓 오류가 발생했습니다." }]);
      },
      onDisconnect: () => {
        setConnected(false);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      setConnected(false);
      client.deactivate();
      clientRef.current = null;
    };
  }, [roomId, user?.username]);

  const sendMessage = () => {
    const message = input.trim();
    if (!message || !clientRef.current || !connected) return;

    clientRef.current.publish({
      destination: `/pub/room/${roomId}`,
      body: JSON.stringify({ content: message }),
    });
    setInput("");
  };

  useEffect(() => {
    if (!messagesRef.current) return;
    messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
  }, [messages]);

  const renderedMessages = useMemo(
    () =>
      messages.map((msg, idx) => {
        if (msg.system || !msg.sender) {
          return (
            <div key={`system-${idx}`} className="msg msg-system">
              {msg.content}
            </div>
          );
        }
        const mine = msg.sender === user?.username;
        return (
          <div key={`${msg.sender}-${idx}`} className={`msg ${mine ? "msg-mine" : "msg-other"}`}>
            {!mine && <div className="msg-sender">{msg.sender}</div>}
            <div>{msg.content}</div>
          </div>
        );
      }),
    [messages, user?.username],
  );

  return (
    <section>
      <div className="chat-container">
        <div className="chat-header">
          <div className="chat-header-left">
            <span className="material-icons">chat</span>
            <span className="chat-room-name">{roomName}</span>
          </div>
          <Link className="back-btn" to="/rooms">
            <span className="material-icons" style={{ fontSize: 16 }}>
              arrow_back
            </span>
            <span>목록으로</span>
          </Link>
        </div>

        <div className="chat-messages" ref={messagesRef}>
          {renderedMessages}
        </div>

        <div className="chat-input-area">
          <input
            className="chat-input"
            disabled={!connected}
            placeholder={connected ? "메시지를 입력하세요" : "연결 중..."}
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") sendMessage();
            }}
          />
          <button className="send-btn" disabled={!connected} type="button" onClick={sendMessage}>
            <span className="material-icons" style={{ fontSize: 20 }}>
              send
            </span>
          </button>
        </div>
      </div>
    </section>
  );
}
