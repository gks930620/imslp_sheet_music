import { useEffect, useRef, useState } from "react";
import { Link, Navigate, useLocation, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { PageTabs } from "../../components/sheetmusic/PageTabs.jsx";
import { WorkCard } from "../../components/sheetmusic/WorkCard.jsx";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { TOAST_UNDO_MS, showToast } from "../../components/common/Toast.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useSection, useSectionPath } from "../../hooks/useSection.js";
import { useDocumentTitle } from "../../hooks/useDocumentTitle.js";
import { useAuth } from "../../context/AuthContext.jsx";
import { authFetch, callApi } from "../../lib/http.js";
import { formatEditionBrief, formatReceivedDate } from "../../lib/format.js";
import { linkSection } from "../../lib/sections.js";
import { LOGIN_REASON, loginHref, saveLoginIntent } from "../../lib/loginIntent.js";

/** 즐겨찾기 켜기·끄기·해제·되돌리기가 모두 같은 문구를 쓴다 (00 §3-11 ②) */
const FAVORITE_SAVE_FAILED = "즐겨찾기를 저장하지 못했어요. 다시 시도해 주세요";
const REDOWNLOAD_FAILED = "지금은 파일을 받을 수 없어요. 잠시 후 다시 시도해 주세요";

const FAVORITES = "favorites";
const DOWNLOADS = "downloads";
const TAB_LABELS = { [FAVORITES]: "즐겨찾기", [DOWNLOADS]: "받은 악보" };

/**
 * 내 악보 — 09_내악보_즐겨찾기_받은악보.md. "돌아온 사람" 의 선반이다.
 *
 * 주소가 곧 탭이다(02 §10-4): `/{구분}/library/favorites` · `/{구분}/library/downloads`.
 * 라우트를 두 개만 등록하므로(App.jsx) 주소의 마지막 조각 말고 다른 근원을 두지 않는다 — 03 §21-6 과 같은 원칙.
 */
function tabFromPathname(pathname) {
  return pathname.endsWith(`/${DOWNLOADS}`) ? DOWNLOADS : FAVORITES;
}

export function MyLibraryPage() {
  const { status, isAuthenticated } = useAuth();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const section = linkSection(useSection());
  const sectionPath = useSectionPath();
  const tab = tabFromPathname(location.pathname);
  const page = Math.max(0, Number(searchParams.get("page") ?? 0) || 0);
  const returnTo = location.pathname + location.search;
  useDocumentTitle(`${TAB_LABELS[tab]} — 쉬운악보 ${section.label}`);

  // 02 §10-0 — 사용자 id 는 어디에도 싣지 않는다(주체는 토큰). 화면은 size 를 보내지 않는다
  const query = `section=${section.code}&page=${page}`;
  const resource = useApiResource(() => callApi(`/api/me/library/${tab}?${query}`).then((result) => result.data), {
    deps: [tab, query],
    enabled: isAuthenticated,
  });

  // 토스트의 "되돌리기" 는 React 트리 밖(body 에 붙은 버튼)에서 늦게 불린다 — 그때의 최신 목록을 읽을 창구가 필요하다
  const dataRef = useRef(null);
  const [downloadState, setDownloadState] = useState({});
  const pendingFocus = useRef(null);
  const listRef = useRef(null);

  useEffect(() => {
    dataRef.current = resource.data;
  }, [resource.data]);

  // 09 상태표 — 비로그인·세션 풀림이면 이 화면을 그리지 않고 로그인 화면으로. 돌아갈 주소는 **들어오려던 탭**이고,
  // 소셜 왕복에서 쿼리가 사라지므로 sessionStorage 에도 같은 주소를 적어 둔다(03 §22-2).
  const sessionGone = status !== "loading" && (!isAuthenticated || resource.error?.status === 401);
  useEffect(() => {
    if (!sessionGone) return;
    saveLoginIntent({ returnTo });
  }, [sessionGone, returnTo]);

  // 해제로 항목이 빠지면 포커스를 **다음 항목의 해제 버튼**으로 옮긴다 — 허공에 두지 않는다(09 §1-1-1).
  // 그 자리는 목록이 다시 그려진 뒤에야 존재하므로 커밋 뒤에 옮긴다(상태가 아니라 한 번 쓰고 비우는 메모라 ref).
  useEffect(() => {
    const index = pendingFocus.current;
    if (index === null) return;
    pendingFocus.current = null;
    const buttons = listRef.current?.querySelectorAll(".library-remove-btn") ?? [];
    const target = buttons[Math.min(index, buttons.length - 1)];
    (target ?? document.querySelector(".my-library .page-tab.selected"))?.focus();
  });

  if (status === "loading") {
    // 인증 확인이 끝나기 전에는 로그인 화면으로 보내지도, h1·탭을 그리지도 않는다(기획 05 §1-2)
    return (
      <div className="my-library my-library-checking">
        <div className="spinner" role="status" aria-label="인증 상태 확인 중" />
      </div>
    );
  }

  if (sessionGone) {
    return <Navigate replace to={loginHref(returnTo, LOGIN_REASON.LIBRARY)} />;
  }

  const data = resource.data;
  const counts = data?.counts ?? null;
  const pageData = tab === DOWNLOADS ? data?.items : data?.works;

  /** 목록·숫자는 늘 함께 움직인다(낙관 반영) — 서버 응답을 기다렸다 그리면 목록과 버튼이 어긋난다 */
  const patchData = (mutate) => {
    const current = dataRef.current;
    if (!current) return;
    const next = mutate(current);
    if (next === current) return;
    dataRef.current = next;
    resource.setData(next);
  };

  const withoutWork = (current, workId) => {
    const content = current.works.content.filter((item) => item.id !== workId);
    if (content.length === current.works.content.length) return current;
    return {
      ...current,
      counts: { ...current.counts, favorites: Math.max(0, (current.counts?.favorites ?? 0) - 1) },
      works: { ...current.works, content, totalElements: Math.max(0, (current.works.totalElements ?? 1) - 1) },
    };
  };

  const withWorkAt = (current, work, index) => {
    if (current.works.content.some((item) => item.id === work.id)) return current;
    const content = [...current.works.content];
    content.splice(index, 0, work);
    return {
      ...current,
      counts: { ...current.counts, favorites: (current.counts?.favorites ?? 0) + 1 },
      works: { ...current.works, content, totalElements: (current.works.totalElements ?? 0) + 1 },
    };
  };

  // 09 §6 S6 — 해제는 **즉시 서버 반영**이고 되돌리기는 다시 넣기다(5초 뒤 지연 삭제가 아니다).
  // 그래야 토스트가 떠 있는 동안 곡 상세를 열어도 "즐겨찾기됨" 이 목록과 어긋나지 않는다.
  const undoRemove = (work, index) => {
    patchData((current) => withWorkAt(current, work, index));
    callApi(`/api/me/favorites/${work.id}`, { method: "PUT" }).catch(() => {
      patchData((current) => withoutWork(current, work.id));
      showToast(FAVORITE_SAVE_FAILED);
    });
  };

  const removeFavorite = (work, index) => {
    patchData((current) => withoutWork(current, work.id));
    pendingFocus.current = index;
    showToast("즐겨찾기에서 뺐어요", {
      durationMs: TOAST_UNDO_MS,
      action: { label: "되돌리기", onClick: () => undoRemove(work, index) },
    });
    callApi(`/api/me/favorites/${work.id}`, { method: "DELETE" }).catch(() => {
      patchData((current) => withWorkAt(current, work, index));
      showToast(FAVORITE_SAVE_FAILED);
    });
  };

  /** 02 §10-3 — "다시 받기" 성공은 곡 상세와 같은 방식으로 안다: 같은 주소로 HEAD 한 번(§3-4) */
  const redownload = async (row) => {
    const url = row.redownloadUrl;
    if (!url) return;
    const workId = row.work.id;
    setDownloadState((prev) => ({ ...prev, [workId]: "checking" }));
    try {
      const response = await authFetch(url, { method: "HEAD" });
      setDownloadState((prev) => ({ ...prev, [workId]: response.ok ? "idle" : "failed" }));
      if (response.ok) markReceivedNow(workId);
    } catch {
      setDownloadState((prev) => ({ ...prev, [workId]: "failed" }));
    }
  };

  /**
   * 성공하면 그 자리에서 날짜가 `오늘 받음` 이 된다. 보고 있는 목록을 재정렬하지는 않는다(09 §1-2-1).
   *
   * 날짜 밖에 손대는 것은 ③-a 뿐이다 — 09 §1-2-2 표의 ③ 행이 "성공하면 이 항목은 그 자리에서 ① 형태로
   * 바뀐다(받은 판본 = 방금 받은 추천 판본)" 라고 명시한 경우다. ①② 에서 받은 것은 여전히 **그때 판본**이라
   * (같은 표 ② "바꿔치기하지 않는다") 상태·판본 줄·안내를 그대로 둔다. 여기서 ① 로 바꾸면 서버가 다음 조회에도
   * ② 를 주므로 새로고침 한 번에 안내가 되살아난다 — 같은 사실을 화면이 두 번 다르게 말하게 된다.
   */
  const markReceivedNow = (workId) => {
    patchData((current) => ({
      ...current,
      items: {
        ...current.items,
        content: current.items.content.map((row) => {
          if (row.work.id !== workId) return row;
          const downloadedAt = new Date().toISOString();
          // ①② — 받은 판본은 바뀌지 않았다. 날짜만 갱신한다
          if (row.redownloadState !== "UNAVAILABLE") return { ...row, downloadedAt };
          // ③-a — 못 받던 것 대신 지금 추천 판본을 받았으니 이 줄은 그 자리에서 ① 형태가 된다
          return {
            ...row,
            downloadedAt,
            receivedEdition: row.alternativeEdition ?? row.receivedEdition,
            redownloadState: "AVAILABLE",
            alternativeEdition: null,
          };
        }),
      },
    }));
  };

  const tabs = [
    { key: FAVORITES, label: TAB_LABELS[FAVORITES], href: sectionPath("/library/favorites"), count: counts?.favorites },
    { key: DOWNLOADS, label: TAB_LABELS[DOWNLOADS], href: sectionPath("/library/downloads"), count: counts?.downloads },
  ];

  const isEmpty = Boolean(pageData) && (pageData.content?.length ?? 0) === 0;
  const isOutOfRange = isEmpty && (pageData.totalElements ?? 0) > 0;

  return (
    <div className="my-library">
      <h1 className="my-library-title">내 악보</h1>
      <PageTabs label="내 악보" current={tab} tabs={tabs} />

      {resource.error ? (
        <ErrorState onRetry={resource.reload} />
      ) : !pageData ? (
        <div className="skeleton-list" aria-hidden="true">
          {[0, 1, 2, 3, 4].map((index) => (
            <div key={index} className="skeleton-row" />
          ))}
        </div>
      ) : isOutOfRange ? (
        <EmptyState icon="search_off" title="이 페이지에는 곡이 없어요">
          <button className="btn btn-text" type="button" onClick={() => goToPage(0, setSearchParams, searchParams)}>
            첫 페이지로
          </button>
        </EmptyState>
      ) : isEmpty ? (
        <LibraryEmptyState tab={tab} homeHref={sectionPath()} />
      ) : (
        <>
          <div className="post-list library-list" ref={listRef}>
            {tab === DOWNLOADS
              ? pageData.content.map((row) => (
                  <ReceivedRow
                    key={row.work.id}
                    row={row}
                    workHref={sectionPath(`/works/${row.work.id}`)}
                    state={downloadState[row.work.id] ?? "idle"}
                    onDownload={() => redownload(row)}
                  />
                ))
              : pageData.content.map((work, index) => (
                  <div className="library-item" key={work.id}>
                    <WorkCard work={work} hideAlias />
                    {/* 09 F1 — 해제 버튼은 카드 링크 **밖**의 별도 열이다(중첩 인터랙티브 금지·오터치 방지) */}
                    <div className="library-remove">
                      <button
                        type="button"
                        className="library-remove-btn"
                        aria-label="즐겨찾기 해제"
                        title="즐겨찾기 해제"
                        onClick={() => removeFavorite(work, index)}
                      >
                        <span className="material-icons" aria-hidden="true">
                          star
                        </span>
                      </button>
                    </div>
                  </div>
                ))}
          </div>
          <Pagination
            page={pageData.page}
            totalPages={pageData.totalPages}
            onChange={(next) => {
              goToPage(next, setSearchParams, searchParams);
              window.scrollTo(0, 0);
            }}
          />
        </>
      )}
    </div>
  );
}

function goToPage(page, setSearchParams, searchParams) {
  const next = new URLSearchParams(searchParams);
  if (page > 0) next.set("page", String(page));
  else next.delete("page");
  setSearchParams(next);
}

/** 09 §1-3 — 빈 표를 두지 않는다. 탭과 `(0)` 은 그대로 보인다 */
function LibraryEmptyState({ tab, homeHref }) {
  const favorites = tab === FAVORITES;
  return (
    <EmptyState
      icon={favorites ? "star_border" : "download"}
      title={favorites ? "아직 즐겨찾기한 곡이 없어요" : "아직 받은 악보가 없어요"}
      description={
        favorites ? (
          <>
            {"곡 상세에서 "}
            <span className="material-icons library-empty-icon" aria-hidden="true">
              star_border
            </span>
            {" 즐겨찾기를 누르면 여기 모여요"}
          </>
        ) : (
          <>
            {/* 비로그인·기능 도입 전 기록이 왜 없는지의 답이다(기획 05 §2-4, 8-D 11) */}
            <span className="library-empty-strong">로그인한 상태에서</span>
            {" 받은 악보가 여기 남아요"}
          </>
        )
      }
    >
      <Link className="btn btn-primary" to={homeHref}>
        인기곡 보러 가기
      </Link>
    </EmptyState>
  );
}

/**
 * 받은 악보 한 항목 — 곡 카드(링크) + 링크 밖 "받기 영역". 09 §1-2-2 의 3상태를 **버튼 하나·문장 하나**로 가른다:
 * ① 다시 받기만 / ② 다시 받기 + 한 줄 / ③ 문장 + (지금 추천 판본 받기 | 곡 보기).
 */
function ReceivedRow({ row, workHref, state, onDownload }) {
  const receivedLine = formatEditionBrief(row.receivedEdition);
  const alternativeLine = formatEditionBrief(row.alternativeEdition);
  const unavailable = row.redownloadState === "UNAVAILABLE";
  const changed = row.redownloadState === "RECOMMENDATION_CHANGED";
  const canDownload = Boolean(row.redownloadUrl);
  const downloading = state === "checking";

  return (
    <div className="library-item library-item-received">
      <WorkCard work={row.work} hideAlias />
      <div className="library-receive">
        <p className="library-receive-date">{formatReceivedDate(row.downloadedAt)}</p>
        {receivedLine ? (
          <p className="library-receive-edition">
            <span className="library-receive-label">받은 판본:</span> {receivedLine}
          </p>
        ) : null}

        {unavailable ? (
          <p className="library-receive-blocked">
            <span className="material-icons" aria-hidden="true">
              block
            </span>
            그때 받은 악보는 지금 받을 수 없어요
          </p>
        ) : null}

        {unavailable && alternativeLine ? (
          <p className="library-receive-edition">
            <span className="library-receive-label">지금 추천 판본:</span> {alternativeLine}
          </p>
        ) : null}

        {canDownload ? (
          <a className="btn btn-primary library-receive-btn" href={row.redownloadUrl} download onClick={onDownload}>
            <span className="material-icons" aria-hidden="true">
              download
            </span>
            {downloading ? "받는 중…" : unavailable ? "지금 추천 판본 받기" : "다시 받기"}
          </a>
        ) : (
          <Link className="btn btn-outline library-receive-btn" to={workHref}>
            곡 보기
          </Link>
        )}

        {changed ? (
          // 상자로 만들지 않는다 — 항목마다 상자가 붙으면 목록이 경고판이 된다(09 §1-2-2)
          <p className="library-receive-note">
            <span className="material-icons" aria-hidden="true">
              info
            </span>
            지금 추천 판본은 이것과 달라요 —{" "}
            <Link className="btn btn-text" to={workHref}>
              곡 보기
            </Link>
          </p>
        ) : null}

        {state === "failed" ? (
          <InlineAlert variant="danger">
            <p>{REDOWNLOAD_FAILED}</p>
            <Link className="btn btn-text" to={workHref}>
              곡 보기
            </Link>
          </InlineAlert>
        ) : null}
      </div>
    </div>
  );
}
