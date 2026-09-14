import { Fragment } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useSection, useSectionPath } from "../../hooks/useSection.js";
import { useDocumentTitle } from "../../hooks/useDocumentTitle.js";
import { callPublicApi } from "../../lib/http.js";
import { formatLifeSpan } from "../../lib/format.js";
import { getChosung } from "../../lib/hangul.js";
import { linkSection } from "../../lib/sections.js";

/** 04_작곡가.md 화면 A — 서버 정렬 그대로, 초성 구분 헤더는 화면에서 계산. 페이지 이동 없음 */
export function ComposerListPage() {
  const section = linkSection(useSection());
  const sectionPath = useSectionPath();
  useDocumentTitle(`작곡가 — 쉬운악보 ${section.label}`);

  // 02 §0-7 — "공개 곡 1개 이상" 이 그 구분 기준으로 좁아지고, workCount 도 그 구분 기준이다
  const list = useApiResource(
    () => callPublicApi(`/api/composers?section=${section.code}`).then((result) => result.data),
    { deps: [section.code] },
  );

  const composers = list.data?.composers ?? [];
  // 서버 정렬을 그대로 두고, 앞 항목과 초성이 다를 때만 구분 헤더를 넣는다
  const rows = composers.map((composer, index) => {
    const header = getChosung(composer.nameKo || composer.nameOriginal);
    const previous = index > 0 ? getChosung(composers[index - 1].nameKo || composers[index - 1].nameOriginal) : null;
    return { composer, header, showHeader: header !== previous };
  });

  return (
    <div className="composer-list">
      <div className="page-header">
        <h1>
          <span className="material-icons">people</span>
          작곡가
        </h1>
      </div>

      {list.error ? (
        <ErrorState onRetry={list.reload} />
      ) : list.loading || !list.data ? (
        <div className="skeleton-list" aria-hidden="true">
          {[0, 1, 2, 3, 4, 5, 6, 7].map((index) => (
            <div key={index} className="skeleton-row" />
          ))}
        </div>
      ) : composers.length === 0 ? (
        <EmptyState icon="people_outline" title="아직 등록된 작곡가가 없어요" description="곡이 등록되면 작곡가도 함께 보여요" />
      ) : (
        <>
          <p className="composer-list-subtitle">{`공개된 곡이 있는 작곡가 ${list.data.total}명`}</p>
          <div className="composer-list-body">
            {rows.map(({ composer, header, showHeader }) => {
              const sub = [composer.nameOriginal, formatLifeSpan(composer.birthYear, composer.deathYear)]
                .filter(Boolean)
                .join(" · ");
              return (
                <Fragment key={composer.id}>
                  {showHeader ? <p className="composer-list-header">{header}</p> : null}
                  <Link className="composer-list-row" to={sectionPath(`/composers/${composer.id}`)}>
                    <span className="composer-list-name">{composer.nameKo || composer.nameOriginal}</span>
                    <span className="composer-list-count">
                      {`${composer.workCount}곡`}
                      <span className="material-icons" aria-hidden="true">
                        chevron_right
                      </span>
                    </span>
                    <span className="composer-list-sub">
                      {composer.nameKo ? sub : formatLifeSpan(composer.birthYear, composer.deathYear)}
                    </span>
                  </Link>
                </Fragment>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
