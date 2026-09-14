import { Link, useSearchParams } from "react-router-dom";
import { NotFoundView } from "../../components/common/NotFoundView.jsx";
import { useSection } from "../../hooks/useSection.js";
import { useDocumentTitle } from "../../hooks/useDocumentTitle.js";
import { DEFAULT_SECTION } from "../../lib/sections.js";
import { imslpSearchUrl } from "../../lib/imslp.js";
import { buildWorkQuery } from "../../lib/workQuery.js";

/** 08 §2-3 — 검색어를 작은따옴표로, 15자 넘으면 앞 15자 + … */
const KEYWORD_MAX = 15;
/** 돌아가기 링크가 그대로 되돌려 주는 검색 상태 (08 §2-3 — 검색어만이 아니라 필터·페이지까지) */
const RESTORED_KEYS = ["q", "in", "level", "pages", "downloadable", "page"];

function shorten(keyword) {
  return keyword.length > KEYWORD_MAX ? `${keyword.slice(0, KEYWORD_MAX)}…` : keyword;
}

/**
 * 화면정의 08 §2 준비 중 구분 안내 — `/violin` · `/orchestra`.
 * 404 가 아니라 정상 화면이다. 서버를 부르지 않으므로 로딩·에러 상태가 없다(02 §7).
 * 본문 검색창·"열리면 알려주기"·인기곡 목록은 두지 않는다 — 출구는 기획이 정한 2개뿐.
 */
export function SectionPreparingPage() {
  const section = useSection();
  const [searchParams] = useSearchParams();
  useDocumentTitle(section ? `${section.label} 준비 중 — 쉬운악보` : null);

  // 라우트가 준비 중 구분에만 이 화면을 붙이지만, 다른 곳에서 열리면 안내가 아니라 404 다
  if (!section || section.open) return <NotFoundView />;

  // "들고 왔다"의 판정 근거는 주소의 q 다 — 히스토리·referrer 로 판단하지 않는다 (08 §2-3)
  const keyword = (searchParams.get("q") ?? "").trim();
  const shortKeyword = shorten(keyword);
  const backHref = `/${DEFAULT_SECTION.slug}/search?${buildWorkQuery(searchParams, RESTORED_KEYS)}`;
  const imslpHref = keyword ? imslpSearchUrl(keyword) : section.imslpUrl;

  return (
    <section className="empty-state section-preparing">
      <span className="material-icons">hourglass_empty</span>
      <h3 className="section-preparing-title">{`${section.label} 악보는 아직 준비 중이에요`}</h3>
      <p className="section-preparing-desc">
        {`지금은 ${DEFAULT_SECTION.label} 악보만 제공하고 있어요. ${section.label} 악보는 준비되는 대로 이 자리에 열립니다.`}
      </p>

      <div className="section-preparing-actions">
        <Link className="btn btn-primary btn-lg" to={`/${DEFAULT_SECTION.slug}`}>
          {`${DEFAULT_SECTION.label} 악보 보러 가기`}
        </Link>
        <a className="btn btn-outline" href={imslpHref} target="_blank" rel="noreferrer">
          {keyword ? `IMSLP 에서 '${shortKeyword}' 찾아보기` : `IMSLP 에서 ${section.label} 악보 직접 찾아보기`}
          <span className="material-icons" aria-hidden="true">
            open_in_new
          </span>
        </a>
      </div>

      {keyword ? (
        <Link className="btn btn-text section-preparing-back" to={backHref}>
          <span className="material-icons" aria-hidden="true">
            chevron_left
          </span>
          {`'${shortKeyword}' 검색 결과로 돌아가기`}
        </Link>
      ) : null}
    </section>
  );
}
