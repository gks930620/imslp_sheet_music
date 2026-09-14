import { Link, useLocation } from "react-router-dom";
import { SECTIONS } from "../../lib/sections.js";
import { useSection } from "../../hooks/useSection.js";
import { buildWorkQuery } from "../../lib/workQuery.js";

function classNames(...values) {
  return values.filter(Boolean).join(" ");
}

/**
 * 준비 중 탭이 검색 결과 화면에서만 이어 싣는 검색 상태 6개 (08 §2-3 · 인수 조건 8-A 8·9).
 * `SectionPreparingPage` 의 RESTORED_KEYS 와 **같은 목록**이라야 그 화면이 이 주소로 "돌아가기" 링크를 되만든다.
 * `sort`·`from` 은 뺀다 — workQuery 의 FORWARDED_KEYS(=`sort` 포함)를 그대로 쓰면 정렬 쿼리가 다음 구분으로 샌다.
 */
const RESTORED_KEYS = ["q", "in", "level", "pages", "downloadable", "page"];

/**
 * 00_공통 §3-15 · 08 §1 악기 구분 바 — 필터가 아니라 **화면 이동**이다.
 * - 진짜 링크(`<a href>`): 우클릭 새 탭·링크 복사가 동작해야 한다 (08 §1-2 3번). onClick 만 있는 요소는 정의서 위반.
 * - `role="tab"`/`"tablist"` 금지 — 그 역할은 "같은 화면에서 패널만 바꾼다"는 뜻 (08 §1-2 4번).
 * - 현재 구분은 주소에서 읽는다(03 §21-6). 구분 밖(404·관리)에서는 어느 탭에도 선택 표시가 없다.
 * - 준비 중 탭은 누를 수 있다(08 §1-4 D2). 대신 "준비 중"을 글자로 미리 알린다.
 * - 주소에 든 구분 문자열은 화면에 되뱉지 않는다(08 §3) — 라벨은 고정 목록에서만 나온다.
 */
export function SectionTabs() {
  const current = useSection();
  const { pathname, search } = useLocation();

  // 준비 중 탭이 검색 상태를 실어 가는 건 검색 결과 화면(`/{구분}/search`)에서뿐이다 (인수 조건 8-A 8·9).
  // 홈·곡 상세·작곡가·관리·404 에서는 쿼리 없이 `/{구분}` 으로만 간다.
  const onSearchScreen = Boolean(current) && pathname === `/${current.slug}/search`;
  const carriedQuery = onSearchScreen ? buildWorkQuery(new URLSearchParams(search), RESTORED_KEYS) : "";

  return (
    <nav className="section-tabs" aria-label="악기 구분">
      <div className="section-tabs-inner">
        {SECTIONS.map((section) => {
          const selected = current?.slug === section.slug;
          // 열린 구분(피아노) 탭은 언제나 그 구분의 홈으로(8-B 7). 준비 중 탭만 검색 상태를 이어 싣는다.
          const to = !section.open && carriedQuery ? `/${section.slug}?${carriedQuery}` : `/${section.slug}`;
          return (
            <Link
              key={section.slug}
              className={classNames("section-tab", selected && "selected", !section.open && "preparing")}
              to={to}
              aria-current={selected ? "page" : undefined}
            >
              <span className="section-tab-label">{section.label}</span>
              {section.open ? null : (
                <>
                  {" "}
                  <span className="section-tab-preparing">준비 중</span>
                </>
              )}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
