import { Link } from "react-router-dom";
import { LevelChip } from "./LevelChip.jsx";
import { StatusBadge } from "./StatusBadge.jsx";

function composerText(composer) {
  if (!composer) return "";
  if (!composer.nameKo) return composer.nameOriginal ?? "";
  return `${composer.nameKo} (${composer.nameOriginal})`;
}

/**
 * 00_공통 §3-2 곡 카드. 카드 전체가 곡 상세 링크.
 * variant="compact" 는 홈 인기곡·같은 작곡가의 다른 곡 (썸네일·원어 제목 없음)
 */
export function WorkCard({ work, variant = "full", rank, hideComposer = false, hideAlias = false }) {
  const title = work.titleKo || work.titleOriginal;
  const showOriginal = Boolean(work.titleKo && work.titleOriginal);
  const catalog = (work.catalogNumbers ?? []).join(" · ");

  if (variant === "compact") {
    const composer = hideComposer ? "" : work.composer?.nameKo || work.composer?.nameOriginal || "";
    return (
      <Link className="work-card work-card-compact" to={`/works/${work.id}`}>
        {rank ? <span className="work-card-rank">{rank}</span> : null}
        <span className="work-card-title">{title}</span>
        {composer ? <span className="work-card-composer">{composer}</span> : null}
        <LevelChip level={work.level} />
        <StatusBadge status={work.status} />
      </Link>
    );
  }

  const meta = [hideComposer ? "" : composerText(work.composer), catalog].filter(Boolean).join(" · ");

  return (
    <Link className="work-card" to={`/works/${work.id}`}>
      <span className="work-card-thumb">
        {work.previewUrl ? (
          <img src={work.previewUrl} alt={`${title} 미리보기`} />
        ) : (
          <span className="material-icons">music_note</span>
        )}
      </span>
      <span className="work-card-body">
        <span className="work-card-title-row">
          <span className="work-card-title">{title}</span>
          <StatusBadge status={work.status} />
        </span>
        {showOriginal ? <span className="work-card-original">{work.titleOriginal}</span> : null}
        {meta ? <span className="work-card-meta">{meta}</span> : null}
        <span className="work-card-tags">
          <LevelChip level={work.level} />
          {work.pageCount ? <span className="work-card-pages">{work.pageCount}쪽</span> : null}
        </span>
        {work.matchedAlias && !hideAlias ? <span className="work-card-alias">{`'${work.matchedAlias}'으로 찾음`}</span> : null}
      </span>
    </Link>
  );
}
