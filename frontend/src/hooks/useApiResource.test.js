import { renderHook, waitFor, act } from "@testing-library/react";
import { useApiResource } from "./useApiResource.js";

// frontend-dev 자체 테스트: 화면마다 반복되는 fetch/loading/error 패턴을 훅 하나로 모은다
// (code-convention §7). 계약이 아니라 내부 구현 세부라 여기서 직접 검증한다.
describe("useApiResource", () => {
  it("성공하면 data 를 담고 loading 이 끝난다", async () => {
    const load = vi.fn().mockResolvedValue({ id: 1 });
    const { result } = renderHook(() => useApiResource(load, { deps: [] }));

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({ id: 1 });
    expect(result.current.error).toBeNull();
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("실패하면 error 를 담는다", async () => {
    const load = vi.fn().mockRejectedValue({ status: 404, message: "없어요" });
    const { result } = renderHook(() => useApiResource(load, { deps: [] }));

    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.error.status).toBe(404);
    expect(result.current.data).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("reload() 로 같은 요청을 다시 부른다", async () => {
    const load = vi.fn().mockResolvedValue("ok");
    const { result } = renderHook(() => useApiResource(load, { deps: [] }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.reload());
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
  });

  it("deps 가 바뀌면 다시 부른다", async () => {
    const load = vi.fn().mockResolvedValue("ok");
    const { rerender } = renderHook(({ id }) => useApiResource(() => load(id), { deps: [id] }), {
      initialProps: { id: 1 },
    });
    await waitFor(() => expect(load).toHaveBeenCalledWith(1));

    rerender({ id: 2 });
    await waitFor(() => expect(load).toHaveBeenCalledWith(2));
  });

  it("enabled: false 면 부르지 않고 loading 도 아니다", async () => {
    const load = vi.fn().mockResolvedValue("ok");
    const { result } = renderHook(() => useApiResource(load, { deps: [], enabled: false }));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(load).not.toHaveBeenCalled();
    expect(result.current.data).toBeNull();
  });
});
