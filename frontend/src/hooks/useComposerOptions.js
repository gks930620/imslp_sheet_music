import { useApiResource } from "./useApiResource.js";
import { callApi } from "../lib/http.js";

/** 02_API §4-2 — 관리 화면 select 는 한 번에 채운다 (기본 20, 최대 200) */
const PAGE_SIZE = 200;

/**
 * 작곡가 선택지 (곡 관리 필터·대기함 필터·곡 편집의 작곡가 고르기 공통).
 * 표기는 한글 표기, 없으면 원어 표기 (05-D·05-E·06-C).
 *
 * @returns {{ options: {id:number, label:string, nameKo:string|null, nameOriginal:string}[], loading:boolean }}
 */
export function useComposerOptions() {
  const resource = useApiResource(
    () => callApi(`/api/admin/composers?size=${PAGE_SIZE}`).then((result) => result.data?.content ?? []),
    { deps: [] },
  );

  const options = (resource.data ?? []).map((composer) => ({
    id: composer.id,
    label: composer.nameKo || composer.nameOriginal,
    nameKo: composer.nameKo ?? null,
    nameOriginal: composer.nameOriginal,
  }));

  return { options, loading: resource.loading };
}
