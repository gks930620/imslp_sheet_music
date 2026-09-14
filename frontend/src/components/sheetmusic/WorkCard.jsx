import { Link } from "react-router-dom";
import { LevelChip } from "./LevelChip.jsx";
import { StatusBadge } from "./StatusBadge.jsx";
import { useSectionPath } from "../../hooks/useSection.js";
import { formatWorkScopeLine, isCollectionWork } from "../../lib/format.js";

function composerText(composer) {
  if (!composer) return "";
  if (!composer.nameKo) return composer.nameOriginal ?? "";
  return `${composer.nameKo} (${composer.nameOriginal})`;
}

/**
 * 00_공통 §3-2 곡 카드. 카드 전체가 곡 상세 링크 — 현재 구분 아래 주소(`/{구분}/works/:id`, 02 §0-5).
 * 구분은 주소에서 직접 읽는다(03 §21-6 — prop 으로 받으면 빠뜨린 호출부의 링크만 구분 밖으로 나간다).
 * variant="compact" 는 홈 인기곡·같은 작곡가의 다른 곡 (썸네일·원어 제목 없음)
 */
export function WorkCard({ work, variant = "full", rank, hideComposer = false, hideAlias = false }) {
  const sectionPath = useSectionPath();
  const href = sectionPath(`/works/${work.id}`);
  const title = work.titleKo || work.titleOriginal;
  const showOriginal = Boolean(work.titleKo && work.titleOriginal);
  const catalog = (work.catalogNumbers ?? []).join(" · ");

  if (variant === "compact") {
    const composer = hideComposer ? "" : work.composer?.nameKo || work.composer?.nameOriginal || "";
    return (
      <Link className="work-card work-card-compact" to={href}>
        {rank ? <span className="work-card-rank">{rank}</span> : null}
        <span className="work-card-title">{title}</span>
        {composer ? <span className="work-card-composer">{composer}</span> : null}
        <LevelChip level={work.level} />
        <StatusBadge status={work.status} />
      </Link>
    );
  }

  const meta = [hideComposer ? "" : composerText(work.composer), catalog].filter(Boolean).join(" · ");
  // 00_공통 §3-2 · 02 §2-2-1 — 별칭 일치 줄과 "받게 되는 악보의 범위" 는 같은 자리·같은 한 줄이다
  const scopeLine = formatWorkScopeLine({
    matchedAlias: hideAlias ? null : work.matchedAlias,
    scopeNote: work.scopeNote,
  });
  const isCollection = isCollectionWork(work.scopeNote);

  return (
    <Link className="work-card" to={href}>
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
          {/* 02 §2-2-1 · 기획 §12-2 — 묶음 악보의 난이도·쪽수는 묶음 전체 기준이다.
              둘 다에 걸리는 꼬리표라 줄 끝에 한 번만 붙인다 (쪽수가 없으면 난이도 뒤). */}
          {isCollection ? <span className="work-card-scope-basis">(전곡 기준)</span> : null}
        </span>
        {scopeLine ? <span className="work-card-alias">{scopeLine}</span> : null}
      </span>
    </Link>
  );
}
