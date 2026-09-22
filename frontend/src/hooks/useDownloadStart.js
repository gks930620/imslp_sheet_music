import { useEffect, useRef, useState } from "react";
import { authFetch } from "../lib/http.js";

/**
 * 다운로드 시작 표시 — 곡 상세 `PDF 받기`(03 §251)와 내 악보 `다시 받기`(09 §1-2-1)가 **나눠 쓰는 규칙**.
 * 계약과 근거는 03_기술결정 §28(§12 의 HEAD 사전 확인 위에 얹는다).
 *
 * 이 훅이 하는 일 넷
 *   ⑴ 진행 중 표시 — `받는 중…` + `aria-disabled` + `is-disabled`(앵커에는 `disabled` 가 없다 — §28-2)
 *   ⑵ 끝 판정   — 같은 주소로 보낸 HEAD 의 응답(§12)
 *   ⑶ 안전장치  — **클릭(요청 시작) 시각** 기준 10초가 지나면 무조건 푼다. 실패로 치지 않는다(§28-3)
 *   ⑷ 늦게 온 답 — 10초가 지나 도착한 응답은 성공이든 실패든 버린다(상태도 onSuccess 도 움직이지 않는다)
 *
 * 모양(스피너·문구)은 호출하는 화면이 그린다 — 두 화면의 라벨과 실패 안내의 출구가 다르기 때문이다(§28-4).
 */

/** 진행 중 버튼 문구 (03 §251 · 09 §1-2-1) */
export const DOWNLOADING_LABEL = "받는 중…";

/** 잠금이 영원해지지 않게 푸는 시간 — 실패 판정이 아니다 (§28-3) */
export const DOWNLOAD_RELEASE_MS = 10_000;

/**
 * @param {object} params
 * @param {string} params.url 다운로드 주소(`/api/editions/{id}/download`). 없는 경우 링크를 그리지 않는 것은 호출자 책임
 * @param {string} params.className 버튼 기본 클래스. 진행 중에는 `is-disabled` 를 합성해 돌려준다
 * @param {() => void} [params.onSuccess] HEAD 200 일 때 한 번. 10초가 지나 도착한 응답에서는 부르지 않는다
 * @returns {{state: "idle"|"checking"|"failed", busy: boolean, failed: boolean, linkProps: object}}
 */
export function useDownloadStart({ url, className, onSuccess }) {
  const [state, setState] = useState("idle");
  // 지금 살아 있는 요청의 표식. 10초 타이머가 이것을 비우면 그 뒤 도착한 응답은 주인을 잃는다(§28-1 규칙 7)
  const tokenRef = useRef(null);
  const timerRef = useRef(null);
  // 콜백은 응답이 온 뒤(늦게) 불리므로 늘 최신 것을 본다 — useDebouncedSearchInput 과 같은 방식
  const onSuccessRef = useRef(onSuccess);
  useEffect(() => {
    onSuccessRef.current = onSuccess;
  });

  // 받는 중에 화면이 사라지면 타이머도 함께 사라진다(§28-1 규칙 8)
  useEffect(
    () => () => {
      clearTimeout(timerRef.current);
      timerRef.current = null;
      tokenRef.current = null;
    },
    [],
  );

  const clearTimer = () => {
    clearTimeout(timerRef.current);
    timerRef.current = null;
  };

  /** 응답이 도착했을 때 — 이미 주인이 바뀌었으면(10초 경과) 아무것도 하지 않는다 */
  const settle = (token, ok) => {
    if (tokenRef.current !== token) return;
    tokenRef.current = null;
    clearTimer();
    setState(ok ? "idle" : "failed");
    if (ok) onSuccessRef.current?.();
  };

  const handleClick = (event) => {
    // 받는 중 재클릭은 막고 요청도 더 보내지 않는다(규칙 3). 첫 클릭은 막지 않는다 —
    // 네이티브 다운로드가 그대로 시작돼야 한다(규칙 1 · 02 §7)
    if (tokenRef.current) {
      event.preventDefault();
      return;
    }

    const token = {};
    tokenRef.current = token;
    setState("checking");

    // 기준 시각은 **클릭(요청 시작)** 이다(규칙 5). 자물쇠만 푼다 — failed 로 가지 않고 안내도 띄우지 않는다(규칙 6)
    timerRef.current = setTimeout(() => {
      if (tokenRef.current !== token) return;
      tokenRef.current = null;
      timerRef.current = null;
      setState("idle");
    }, DOWNLOAD_RELEASE_MS);

    authFetch(url, { method: "HEAD" }).then(
      (response) => settle(token, response.ok),
      () => settle(token, false), // 네트워크 오류도 실패다(규칙 4)
    );
  };

  const busy = state === "checking";

  return {
    state,
    busy,
    failed: state === "failed",
    linkProps: {
      href: url, // 진행 중에도 지우지 않는다 — 이미 시작된 다운로드와 포커스를 잃는다(§28-2)
      download: true,
      onClick: handleClick,
      className: busy ? `${className} is-disabled` : className,
      "aria-disabled": busy ? "true" : undefined,
    },
  };
}
