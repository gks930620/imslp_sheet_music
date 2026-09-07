import { useEffect, useRef, useState } from "react";

/**
 * 00_공통 §3-10 미리보기 크게 보기.
 * 닫기: 닫기 버튼 / 바깥 클릭 / Esc. 열 때 포커스를 안으로, 닫으면 원래 요소로.
 */
export function PreviewLightbox({ src, caption, onClose }) {
  const closeRef = useRef(null);
  const openerRef = useRef(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    openerRef.current = document.activeElement;
    closeRef.current?.focus();
    const opener = openerRef.current;
    return () => {
      if (opener && typeof opener.focus === "function") opener.focus();
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === "Escape") onClose?.();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="lightbox-overlay" onClick={() => onClose?.()}>
      <button ref={closeRef} className="lightbox-close" type="button" aria-label="닫기" onClick={() => onClose?.()}>
        <span className="material-icons">close</span>
      </button>
      <div className="lightbox-body" onClick={(event) => event.stopPropagation()}>
        {failed ? null : (
          <img className="lightbox-image" src={src} alt={caption} onError={() => setFailed(true)} />
        )}
        <p className="lightbox-caption">{failed ? "미리보기를 불러오지 못했어요" : caption}</p>
      </div>
    </div>
  );
}
