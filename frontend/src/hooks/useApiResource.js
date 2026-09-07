import { useCallback, useEffect, useRef, useState } from "react";

/**
 * 화면마다 반복되는 "부르기 → 로딩 → 성공/실패 → 다시 시도" 를 한 곳에 모은다.
 * (code-convention §7 — API 호출 자체는 lib/http.js 의 공용 클라이언트를 쓴다)
 *
 * @param {() => Promise<any>} load  요청 함수 (callApi/callPublicApi 를 감싼 것)
 * @param {object}   options
 * @param {any[]}    options.deps    이 값이 바뀌면 다시 부른다 (길이는 고정)
 * @param {boolean}  options.enabled false 면 부르지 않는다
 * @returns {{ data, loading, error, reload, setData }}
 */
export function useApiResource(load, { deps = [], enabled = true } = {}) {
  const [state, setState] = useState({ data: null, loading: enabled, error: null });
  const [nonce, setNonce] = useState(0);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    if (!enabled) {
      setState({ data: null, loading: false, error: null });
      return undefined;
    }

    let alive = true;
    setState((prev) => ({ ...prev, loading: true, error: null }));
    loadRef
      .current()
      .then((data) => {
        if (alive) setState({ data, loading: false, error: null });
      })
      .catch((error) => {
        if (alive) setState({ data: null, loading: false, error: error ?? { message: "오류가 발생했습니다." } });
      });

    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, nonce, ...deps]);

  const reload = useCallback(() => setNonce((value) => value + 1), []);
  const setData = useCallback((data) => setState((prev) => ({ ...prev, data })), []);

  return { ...state, reload, setData };
}
