import { useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { WorkCard } from "../../components/sheetmusic/WorkCard.jsx";
import { LevelChip } from "../../components/sheetmusic/LevelChip.jsx";
import { CopyrightBadge } from "../../components/sheetmusic/CopyrightBadge.jsx";
import { EditionRow } from "../../components/sheetmusic/EditionRow.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { NotFoundView } from "../../components/common/NotFoundView.jsx";
import { PreviewLightbox } from "../../components/common/PreviewLightbox.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { authFetch, callPublicApi } from "../../lib/http.js";
import { formatEditionKind, formatEditionScope, formatFileSizeCompact } from "../../lib/format.js";

/**
 * 기획 §F3-6 · §5 예외표 — previewUrl 이 없는 이유가 "파일이 없다" 가 아니라 "판정이 안 끝났다" 일 때의 문구.
 * 판단 근거는 koreaCopyright 하나다(02 §2-3 — 이유를 알려주는 별도 필드는 두지 않는다).
 */
const PREVIEW_HIDDEN_BY_COPYRIGHT = "저작권을 확인하는 중이라 미리보기도 아직 보여드릴 수 없어요";

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
  const detail = useApiResource(() => callPublicApi(`/api/works/${id}`).then((result) => result.data), { deps: [id] });
  const [expandedOverride, setExpandedOverride] = useState(null);
  const [lightbox, setLightbox] = useState(null);
  const [downloadCheck, setDownloadCheck] = useState("idle"); // "idle" | "checking" | "failed"
  const othersRef = useRef(null);

  const work = detail.data;
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

  if (detail.error) {
    return detail.error.status === 404 ? <NotFoundView /> : <ErrorState onRetry={detail.reload} />;
  }

  if (!work) {
    return (
      <div className="work-detail skeleton-detail" aria-hidden="true">
        <div className="skeleton-row" />
        <div className="skeleton-row" />
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

  // 03 §동작·통신 상태 / 02_API §3-4(HEAD) — <a download> 은 실패를 알 수 없어 같은 주소로 HEAD 한 번을 곁들인다.
  // 네이티브 다운로드는 그대로 진행시키고(preventDefault 하지 않는다) 결과만 안내에 반영한다.
  const checkDownload = async () => {
    setDownloadCheck("checking");
    try {
      const response = await authFetch(recommended.downloadUrl, { method: "HEAD" });
      setDownloadCheck(response.ok ? "idle" : "failed");
    } catch {
      setDownloadCheck("failed");
    }
  };

  return (
    <div className="work-detail">
      <section className="work-detail-info">
        <h1 className="work-detail-title">{title}</h1>
        {work.titleKo && work.titleOriginal ? <p className="work-detail-original">{work.titleOriginal}</p> : null}
        <p className="work-detail-composer-row">
          {work.composer ? (
            <Link className="work-detail-composer" to={`/composers/${work.composer.id}`}>
              {composerName}
              <span className="material-icons" aria-hidden="true">
                chevron_right
              </span>
            </Link>
          ) : null}
          {catalog ? <span className="work-detail-catalog">{catalog}</span> : null}
          <LevelChip level={work.level} />
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
            <span className="material-icons" aria-hidden="true">
              bookmark
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
              <span className="material-icons" aria-hidden="true">
                star
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
                  <a className="btn btn-primary btn-lg" href={recommended.downloadUrl} download onClick={checkDownload}>
                    <span className="material-icons" aria-hidden="true">
                      download
                    </span>
                    {downloadCheck === "checking"
                      ? "받는 중…"
                      : `PDF 받기 · ${formatFileSizeCompact(recommended.fileSize)}`}
                  </a>
                  {downloadCheck === "failed" ? (
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
