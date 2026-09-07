import { useEffect, useRef } from "react";

/**
 * 00_공통 §3-14 ConfirmDialog — 브라우저 confirm() 을 쓰지 않는다.
 * 열릴 때 포커스를 안으로, 닫히면 원래 요소로 (§5 접근성).
 * extraLabel/onExtra: 선택지가 셋인 경우 (05-E 곡 삭제 — [취소] [숨김으로 바꾸기] [그래도 삭제]).
 */
export function ConfirmDialog({
  title,
  description,
  confirmLabel = "확인",
  cancelLabel = "취소",
  extraLabel = "",
  danger = false,
  onConfirm,
  onCancel,
  onExtra,
}) {
  const confirmRef = useRef(null);
  const openerRef = useRef(null);

  useEffect(() => {
    openerRef.current = document.activeElement;
    confirmRef.current?.focus();
    const opener = openerRef.current;
    return () => {
      if (opener && typeof opener.focus === "function") opener.focus();
    };
  }, []);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === "Escape") onCancel?.();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onCancel]);

  return (
    <div className="modal-overlay" onClick={() => onCancel?.()}>
      <div
        className="confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <h3 className="confirm-dialog-title">{title}</h3>
        {description ? <p className="confirm-dialog-body">{description}</p> : null}
        <div className="confirm-dialog-actions">
          <button className="btn btn-secondary" type="button" onClick={() => onCancel?.()}>
            {cancelLabel}
          </button>
          {extraLabel ? (
            <button className="btn btn-secondary" type="button" onClick={() => onExtra?.()}>
              {extraLabel}
            </button>
          ) : null}
          <button
            ref={confirmRef}
            className={danger ? "btn btn-danger" : "btn btn-primary"}
            type="button"
            onClick={() => onConfirm?.()}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
