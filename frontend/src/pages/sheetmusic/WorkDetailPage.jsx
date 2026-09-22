import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { WorkCard } from "../../components/sheetmusic/WorkCard.jsx";
import { LevelChip } from "../../components/sheetmusic/LevelChip.jsx";
import { CopyrightBadge } from "../../components/sheetmusic/CopyrightBadge.jsx";
import { EditionRow } from "../../components/sheetmusic/EditionRow.jsx";
import { FavoriteButton } from "../../components/sheetmusic/FavoriteButton.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { NotFoundView } from "../../components/common/NotFoundView.jsx";
import { PreviewLightbox } from "../../components/common/PreviewLightbox.jsx";
import { showToast } from "../../components/common/Toast.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useSection, useSectionPath } from "../../hooks/useSection.js";
import { useDocumentTitle } from "../../hooks/useDocumentTitle.js";
import { DOWNLOADING_LABEL, useDownloadStart } from "../../hooks/useDownloadStart.js";
import { useAuth } from "../../context/AuthContext.jsx";
import { callApi, callPublicApi } from "../../lib/http.js";
import { formatEditionKind, formatEditionScope, formatFileSizeCompact } from "../../lib/format.js";
import { findSectionByCode, linkSection } from "../../lib/sections.js";
import { LOGIN_REASON, loginHref, peekLoginIntent, saveLoginIntent, takeLoginIntent } from "../../lib/loginIntent.js";
import { rememberRecentWork } from "../../lib/recentWorks.js";

/**
 * 기획 §F3-6 · §5 예외표 — previewUrl 이 없는 이유가 "파일이 없다" 가 아니라 "판정이 안 끝났다" 일 때의 문구.
 * 판단 근거는 koreaCopyright 하나다(02 §2-3 — 이유를 알려주는 별도 필드는 두지 않는다).
 */
const PREVIEW_HIDDEN_BY_COPYRIGHT = "저작권을 확인하는 중이라 미리보기도 아직 보여드릴 수 없어요";

/** 03 §3-6-1 · 00 §3-11 ② — 켜기·끄기·복귀 완성이 모두 같은 실패 문구를 쓴다 */
const FAVORITE_SAVE_FAILED = "즐겨찾기를 저장하지 못했어요. 다시 시도해 주세요";

function editionInfoLine(edition) {
  return [
    edition.publisher,
    edition.publishYear,
    edition.editor ? `편집 ${edition.editor}` : "",
    edition.scanner ? `스캔 ${edition.scanner}` : "",
  ]
    .filter(Boolean)
    .join(" · ");
}

/** 03_곡상세.md — 주인공은 추천 판본 카드 하나와 다운로드 버튼 하나 */
export function WorkDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { status, isAuthenticated } = useAuth();
  const section = useSection();
  const sectionPath = useSectionPath();
  // 02 §0-7 — 곡 상세는 section 을 보내지 않는다. 곡이 스스로 구분을 안다
  const detail = useApiResource(() => callPublicApi(`/api/works/${id}`).then((result) => result.data), { deps: [id] });
  const [expandedOverride, setExpandedOverride] = useState(null);
  const [lightbox, setLightbox] = useState(null);
  const othersRef = useRef(null);
  // null = 서버가 준 값 그대로. 누르면(낙관) 그 값이 이긴다 — 02 §3-3 favorited 는 곡 상세 응답에 함께 온다(09 §6 S5).
  // 어느 곡의 값인지 함께 들고 있는다: "같은 작곡가의 다른 곡" 으로 옮기면 화면은 그대로고 :id 만 바뀐다
  const [favoriteOverride, setFavoriteOverride] = useState(null); // { id, value } | null
  const favoriteBusy = useRef(false);
  const intentConsumedFor = useRef(null);

  const work = detail.data;
  const favorited = favoriteOverride?.id === id ? favoriteOverride.value : work?.favorited ?? false;
  const returnTo = location.pathname + location.search;

  /** 8-B 1·2 · 03 §3-6-2 — 비로그인·세션 풀림은 같은 길이다: 의도를 적어 두고 로그인 화면으로 */
  const goLoginForFavorite = () => {
    saveLoginIntent({ action: "FAVORITE", workId: Number(id), returnTo });
    navigate(loginHref(returnTo, LOGIN_REASON.FAVORITE));
  };

  const saveFavorite = (next) =>
    callApi(`/api/me/favorites/${id}`, { method: next ? "PUT" : "DELETE" });

  const toggleFavorite = () => {
    if (status === "loading") return; // 8-B 7 — 확인 중에는 로그인 화면으로 보내지 않는다
    if (!isAuthenticated) {
      goLoginForFavorite();
      return;
    }
    if (favoriteBusy.current) return; // 응답 전 재클릭은 무시하되 비활성 모양으로 바꾸지 않는다(03 §3-6-1)

    const next = !favorited;
    favoriteBusy.current = true;
    setFavoriteOverride({ id, value: next });
    saveFavorite(next)
      .catch((error) => {
        if (error?.status === 401) {
          goLoginForFavorite();
          return;
        }
        setFavoriteOverride({ id, value: !next }); // 누르기 전 상태로 되돌린다. 화면 이동 없음(8-A 7)
        showToast(FAVORITE_SAVE_FAILED, { variant: "danger" });
      })
      .finally(() => {
        favoriteBusy.current = false;
      });
  };

  /**
   * 03_기술결정 §22 — 로그인하고 돌아온 사람의 즐겨찾기를 **여기 한 곳에서** 완성한다.
   * 소비는 take(읽는 즉시 지우고 그 다음 실행)라 새로고침에 두 번 실행되지 않는다(8-B 4).
   * 비로그인으로 도착했으면(돌아가기·뒤로 가기) take 만 하고 아무것도 하지 않는다(8-B 6).
   */
  useEffect(() => {
    if (intentConsumedFor.current === id || status === "loading" || !work) return;
    const intent = peekLoginIntent();
    if (!intent || intent.action !== "FAVORITE" || Number(intent.workId) !== Number(id)) return;

    takeLoginIntent();
    intentConsumedFor.current = id;
    if (!isAuthenticated) return;

    saveFavorite(true)
      .then(() => {
        setFavoriteOverride({ id, value: true });
        showToast("즐겨찾기에 넣었어요");
      })
      .catch(() => {
        setFavoriteOverride({ id, value: false });
        showToast(FAVORITE_SAVE_FAILED, { variant: "danger" });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, isAuthenticated, work, id]);

  /**
   * 03_기술결정 §23 — 곡 상세가 **정상으로 그려진 뒤에만** 이 브라우저의 최근 본 곡에 남긴다.
   * 404·불러오기 실패는 남기지 않는다(8-E 9). 구분은 곡이 스스로 아는 값이다(02 §3-3 section).
   */
  const recentSectionCode = findSectionByCode(work?.section)?.code ?? linkSection(section).code;
  useEffect(() => {
    if (!work) return;
    rememberRecentWork(recentSectionCode, work.id);
  }, [work, recentSectionCode]);

  // 기획 04 §1-4 · 02 §7 — 응답 section 이 주소의 구분과 다르면 그 구분 주소로 replace(자기 교정).
  // 옛 주소 /works/:id 는 라우터가 먼저 /piano/… 로 보내므로 여기서는 늘 구분 안에 있다. 구분 밖에서 열렸거나
  // 응답에 section 이 없으면(아직 안 붙은 서버) 아무것도 하지 않는다 — 화면이 깨지지 않는 쪽이 계약이다.
  const responseSection = findSectionByCode(work?.section);
  useEffect(() => {
    if (!section || !responseSection || responseSection.slug === section.slug) return;
    navigate(`/${responseSection.slug}/works/${id}`, { replace: true });
  }, [section, responseSection, id, navigate]);

  // 00 §4-1 — 자기 교정 뒤에는 주소의 구분 이름으로 제목을 쓴다(08 §5)
  useDocumentTitle(work ? `${work.titleKo || work.titleOriginal} — 쉬운악보 ${linkSection(section).label}` : null);
  const recommended = work?.recommendedEdition ?? null;
  const preparing = Boolean(work) && (!recommended || !recommended.hasFile);
  const restricted = Boolean(recommended) && !preparing && recommended.koreaCopyright === "RESTRICTED";
  const canDownload = Boolean(recommended) && !preparing && recommended.downloadable && Boolean(recommended.downloadUrl);
  const hasFreeOther = Boolean(work) && !canDownload && (work.downloadableOtherCount ?? 0) > 0;
  // 미리보기는 "한국에서 자유 이용 가능" 판본만 보여준다 (기획 §F3-6). 판본 줄에서는 반복하지 않는다 — 같은 줄의 뱃지가 이미 말한다
  const previewHiddenByCopyright = Boolean(recommended) && recommended.koreaCopyright !== "FREE";

  // 추천이 제한·확인 중인데 바로 받을 수 있는 다른 판본이 있으면 자동 펼침 (03 상태별 UI).
  // effect 로 미루지 않고 파생 상태로 둬야 첫 렌더에 바로 펼쳐진다.
  const expanded = expandedOverride ?? hasFreeOther;

  // 03 §251 · 03_기술결정 §28 — 진행 표시·재클릭 차단·10초 자동 복귀는 내 악보와 같은 훅이 맡는다.
  // 끝 판정은 같은 주소로 보낸 HEAD 한 번(§12)이고, 네이티브 다운로드는 그대로 진행시킨다.
  const download = useDownloadStart({
    url: recommended?.downloadUrl ?? null,
    className: "btn btn-primary btn-lg",
  });

  if (detail.error) {
    // 없는 곡·숨김 곡의 404 — 보조 문구는 곡에 대한 것(00 §2-4 기존). 라우트 404(없는 구분 이름)는 NotFoundPage 가 구분까지 말한다
    return detail.error.status === 404 ? (
      <NotFoundView description="주소가 틀렸거나 내려간 곡이에요" />
    ) : (
      <ErrorState onRetry={detail.reload} />
    );
  }

  if (!work) {
    return (
      <div className="work-detail skeleton-detail" aria-hidden="true">
        <div className="skeleton-row" />
        <div className="skeleton-row" />
        {/* 03 §3-6-1 — 즐겨찾기 버튼 자리(120×40)를 미리 잡는다. 응답 후 버튼이 끼어들며 아래가 밀리지 않게 */}
        <div className="skeleton-row skeleton-favorite" />
        <div className="skeleton-card" />
      </div>
    );
  }

  const title = work.titleKo || work.titleOriginal;
  const composerName = work.composer?.nameKo
    ? `${work.composer.nameKo} (${work.composer.nameOriginal})`
    : work.composer?.nameOriginal ?? "";
  const catalog = (work.catalogNumbers ?? []).join(" · ");
  const factLine = [work.compositionYear, work.musicalKey, work.movements].filter(Boolean).join(" · ");
  // 02 §3-3 · 기획 §F3-4 — 줄이 되는 것은 "우리가 파일을 가진 판본" 뿐이고,
  // 파일 없는 판본은 imslpOnlyCount 한 줄이 대신한다(보낼 곳이 없으면 그 줄도 만들지 않는다).
  const others = work.otherEditions ?? [];
  const imslpOnlyCount = work.imslpOnlyCount ?? 0;
  // 기획 §F3-7 · 02 §3-3-2 — 못 주는 곡(준비 중·이용 제한·확인 중)은 작품 페이지가 아니라
  // "우리가 고른 판본" 의 IMSLP 파일 페이지로 보낸다. 추천이 있으면 추천, 없으면 서버가 고른 후보,
  // 둘 다 없으면 작품 페이지. 후보는 파일이 없을 수 있어(hasFile=false) 다운로드에는 절대 쓰지 않는다.
  const imslpTargetUrl =
    recommended?.imslpFileUrl ?? work.imslpCandidateEdition?.imslpFileUrl ?? work.imslpUrl ?? null;
  const imslpOnlyNote =
    imslpOnlyCount > 0 && work.imslpUrl ? (
      <p className="other-editions-imslp-note">
        {`IMSLP 에는 이 곡의 다른 악보가 ${imslpOnlyCount}개 더 있어요 — `}
        <a href={work.imslpUrl} target="_blank" rel="noreferrer">
          IMSLP 에서 보기
          <span className="material-icons" aria-hidden="true">
            open_in_new
          </span>
        </a>
      </p>
    ) : null;

  const openLightbox = (edition) =>
    setLightbox({
      src: edition.previewUrl,
      caption: `${title} — ${formatEditionKind(edition.kind)} · ${formatEditionScope(edition)} (1쪽/${edition.pageCount}쪽)`,
    });

  return (
    <div className="work-detail">
      <section className="work-detail-info">
        <h1 className="work-detail-title">{title}</h1>
        {work.titleKo && work.titleOriginal ? <p className="work-detail-original">{work.titleOriginal}</p> : null}
        <p className="work-detail-composer-row">
          {work.composer ? (
            <Link className="work-detail-composer" to={sectionPath(`/composers/${work.composer.id}`)}>
              {composerName}
              <span className="material-icons" aria-hidden="true">
                chevron_right
              </span>
            </Link>
          ) : null}
          {catalog ? <span className="work-detail-catalog">{catalog}</span> : null}
          <LevelChip level={work.level} />
          {/* 03 §3-6-1 — 모바일은 이 줄 바로 아래 자기 줄, 데스크톱은 이 줄의 오른쪽 끝(CSS). 비로그인에게도 보인다 */}
          <span className="work-detail-favorite">
            <FavoriteButton favorited={favorited} onToggle={toggleFavorite} />
          </span>
        </p>
        {work.aliases?.length ? (
          <p className="work-detail-aliases">{`이렇게도 불러요: ${work.aliases.join(", ")}`}</p>
        ) : null}
        {factLine ? <p className="work-detail-facts">{factLine}</p> : null}
        {/* 수록곡 안내 (기획 §F3-2 · §10-3, 02 §3-3) — 서버가 준 완성 문장 그대로 한 줄. 공백이면 줄 생략 */}
        {work.collectionGuide?.trim() ? (
          <p className="work-detail-collection">
            <span className="material-icons" aria-hidden="true">
              library_music
            </span>
            {work.collectionGuide}
          </p>
        ) : null}
        {work.movementPageGuide && recommended ? (
          <p className="work-detail-guide">
            {/* 2026-09-20 교체(03 §3-1) — 즐겨찾기 별이 생기자 책갈피가 "저장하는 것" 으로 읽힌다 */}
            <span className="material-icons" aria-hidden="true">
              menu_book
            </span>
            {`악장 안내: ${work.movementPageGuide} (추천 판본 기준)`}
          </p>
        ) : null}
      </section>

      <section className="edition-card">
        {preparing ? (
          <div className="edition-card-preparing">
            <span className="material-icons">hourglass_empty</span>
            <h2 className="edition-card-preparing-title">악보를 준비하고 있어요</h2>
            <p className="edition-card-preparing-desc">IMSLP 원본 페이지에서 먼저 볼 수 있어요</p>
            {imslpTargetUrl ? (
              <a className="btn btn-outline" href={imslpTargetUrl} target="_blank" rel="noreferrer">
                IMSLP에서 보기
                <span className="material-icons" aria-hidden="true">
                  open_in_new
                </span>
              </a>
            ) : null}
            {recommended && editionInfoLine(recommended) ? (
              <p className="edition-card-preparing-info">{editionInfoLine(recommended)}</p>
            ) : null}
          </div>
        ) : (
          <>
            <p className="edition-card-label">
              {/* 2026-09-20 교체(03 §3-6-4) — 별은 "내가 표시한 것", 엄지는 "남(운영자)이 권하는 것" */}
              <span className="material-icons" aria-hidden="true">
                thumb_up
              </span>
              추천 판본
            </p>
            <div className="edition-card-body">
              <div className="edition-card-preview">
                {recommended.previewUrl ? (
                  <button type="button" className="edition-card-preview-btn" onClick={() => openLightbox(recommended)}>
                    <img src={recommended.previewUrl} alt={`${title} 첫 페이지 미리보기`} />
                  </button>
                ) : (
                  <div className="edition-card-preview-empty">
                    <span className="material-icons">image_not_supported</span>
                    <p>{previewHiddenByCopyright ? PREVIEW_HIDDEN_BY_COPYRIGHT : "미리보기 준비 중"}</p>
                  </div>
                )}
              </div>

              <div className="edition-card-detail">
                <p className="edition-card-kind">
                  {`${formatEditionKind(recommended.kind)} · ${formatEditionScope(recommended)}`}
                </p>
                <p className="edition-card-size">
                  {`${recommended.pageCount}쪽 · ${formatFileSizeCompact(recommended.fileSize)}`}
                </p>
                {editionInfoLine(recommended) ? (
                  <p className="edition-card-info">{editionInfoLine(recommended)}</p>
                ) : null}
                <CopyrightBadge koreaCopyright={recommended.koreaCopyright} />
                {recommended.imslpCopyrightText ? (
                  <p className="edition-card-imslp">{`IMSLP 표기: ${recommended.imslpCopyrightText}`}</p>
                ) : null}
                {recommended.ccLicenseName ? (
                  <p className="edition-card-cc">
                    <span className="material-icons" aria-hidden="true">
                      copyright
                    </span>
                    {`${recommended.ccLicenseName}${recommended.ccAttribution ? ` · 편집: ${recommended.ccAttribution}` : ""}`}
                  </p>
                ) : null}
                <p className="edition-card-source">
                  {"출처: IMSLP — "}
                  {work.imslpUrl ? (
                    <a href={work.imslpUrl} target="_blank" rel="noreferrer">
                      원본 페이지 보기
                      <span className="material-icons" aria-hidden="true">
                        open_in_new
                      </span>
                    </a>
                  ) : null}
                  {recommended.imslpFileUrl ? (
                    <>
                      {" · "}
                      <a href={recommended.imslpFileUrl} target="_blank" rel="noreferrer">
                        파일 페이지
                        <span className="material-icons" aria-hidden="true">
                          open_in_new
                        </span>
                      </a>
                    </>
                  ) : null}
                </p>
              </div>
            </div>

            <div className="edition-card-actions">
              {canDownload ? (
                <>
                  <a {...download.linkProps}>
                    {/* 받는 중에는 다운로드 아이콘 자리를 스피너가 대신한다 — 내 악보와 같은 마크업(§28-4) */}
                    {download.busy ? (
                      <span className="btn-spinner" aria-hidden="true" />
                    ) : (
                      <span className="material-icons" aria-hidden="true">
                        download
                      </span>
                    )}
                    {download.busy ? DOWNLOADING_LABEL : `PDF 받기 · ${formatFileSizeCompact(recommended.fileSize)}`}
                  </a>
                  {download.failed ? (
                    <InlineAlert variant="danger">
                      <p>지금은 파일을 받을 수 없어요. 잠시 후 다시 시도해 주세요</p>
                      {work.imslpUrl ? (
                        <a className="btn btn-outline" href={work.imslpUrl} target="_blank" rel="noreferrer">
                          IMSLP에서 보기
                          <span className="material-icons" aria-hidden="true">
                            open_in_new
                          </span>
                        </a>
                      ) : null}
                    </InlineAlert>
                  ) : null}
                  {recommended.largeFile ? (
                    <p className="edition-card-large-file">
                      <span className="material-icons" aria-hidden="true">
                        warning
                      </span>
                      {`파일이 큽니다 (${formatFileSizeCompact(recommended.fileSize)}). 모바일 데이터에 주의하세요`}
                    </p>
                  ) : null}
                </>
              ) : (
                <InlineAlert variant={restricted ? "restricted" : "warning"}>
                  <p>
                    {restricted
                      ? "한국 저작권 기준으로 아직 자유 이용이 어려운 판본이에요. IMSLP 원본 페이지에서 각자 판단해 이용해 주세요"
                      : "이용 가능 여부를 확인하는 중이에요"}
                  </p>
                  {imslpTargetUrl ? (
                    <a className="btn btn-outline" href={imslpTargetUrl} target="_blank" rel="noreferrer">
                      IMSLP에서 보기
                      <span className="material-icons" aria-hidden="true">
                        open_in_new
                      </span>
                    </a>
                  ) : null}
                </InlineAlert>
              )}
            </div>
          </>
        )}
      </section>

      {hasFreeOther ? (
        <InlineAlert variant="info">
          {`바로 받을 수 있는 다른 판본이 ${work.downloadableOtherCount}개 있어요 — `}
          <button
            className="btn btn-text"
            type="button"
            onClick={() => {
              setExpandedOverride(true);
              othersRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
            }}
          >
            보기
          </button>
        </InlineAlert>
      ) : null}

      {others.length || imslpOnlyNote ? (
        <section className="other-editions" ref={othersRef}>
          {others.length ? (
            <>
              <button
                className="btn btn-text collapsible-header"
                type="button"
                aria-expanded={expanded}
                onClick={() => setExpandedOverride(!expanded)}
              >
                <span className="material-icons" aria-hidden="true">
                  {expanded ? "expand_more" : "chevron_right"}
                </span>
                {`다른 판본 보기 (${others.length}개)`}
              </button>
              {expanded ? (
                <>
                  <div className="other-editions-list">
                    {others.map((edition) => (
                      <EditionRow key={edition.id} edition={edition} imslpUrl={work.imslpUrl} onPreview={openLightbox} />
                    ))}
                  </div>
                  {imslpOnlyNote}
                </>
              ) : null}
            </>
          ) : (
            imslpOnlyNote
          )}
        </section>
      ) : null}

      {work.sameComposerWorks?.length ? (
        <section className="same-composer">
          <h2 className="section-title">같은 작곡가의 다른 곡</h2>
          <div className="post-list">
            {work.sameComposerWorks.slice(0, 5).map((item) => (
              <WorkCard key={item.id} work={item} variant="compact" hideComposer />
            ))}
          </div>
        </section>
      ) : null}

      {lightbox ? (
        <PreviewLightbox src={lightbox.src} caption={lightbox.caption} onClose={() => setLightbox(null)} />
      ) : null}
    </div>
  );
}
