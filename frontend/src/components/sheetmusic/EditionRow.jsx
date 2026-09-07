import { CopyrightBadge } from "./CopyrightBadge.jsx";
import { formatEditionKind, formatEditionScope, formatFileSizeCompact } from "../../lib/format.js";

/** 00_공통 §3-6 판본 행 (곡 상세 "다른 판본 보기") */
export function EditionRow({ edition, imslpUrl, onPreview }) {
  const info = [edition.publisher, edition.editor ? `편집 ${edition.editor}` : "", edition.publishYear]
    .filter(Boolean)
    .join(" / ");
  const canDownload = edition.downloadable && edition.hasFile && edition.downloadUrl;
  const externalUrl = edition.imslpFileUrl || imslpUrl;

  return (
    <div className="edition-row">
      {edition.previewUrl ? (
        <button className="edition-row-thumb" type="button" onClick={() => onPreview?.(edition)}>
          <img src={edition.previewUrl} alt={`${formatEditionKind(edition.kind)} 미리보기`} />
        </button>
      ) : null}

      <div className="edition-row-body">
        <p className="edition-row-title">{`${formatEditionKind(edition.kind)} · ${formatEditionScope(edition)}`}</p>
        <p className="edition-row-size">
          {edition.hasFile ? `${edition.pageCount ?? "-"}쪽 · ${formatFileSizeCompact(edition.fileSize)}` : "파일 없음"}
        </p>
        {info ? <p className="edition-row-info">{info}</p> : null}
      </div>

      <div className="edition-row-actions">
        <CopyrightBadge koreaCopyright={edition.koreaCopyright} />
        {canDownload ? (
          <a className="btn btn-primary" href={edition.downloadUrl} download>
            <span className="material-icons" aria-hidden="true">
              download
            </span>
            PDF 받기
          </a>
        ) : externalUrl ? (
          <a className="btn btn-outline" href={externalUrl} target="_blank" rel="noreferrer">
            IMSLP에서 보기
            <span className="material-icons" aria-hidden="true">
              open_in_new
            </span>
          </a>
        ) : null}
      </div>
    </div>
  );
}
