import { Link } from "react-router-dom";

/**
 * 본문 탭 — 00_공통 §3-18. "한 화면 안에서 목록을 바꾸되 **탭마다 주소가 있는** 이동".
 *
 * - 구분 바(§3-15)와 같은 문법: 진짜 링크(`<a href>`) + `aria-current="page"`.
 *   `role="tab"`/`"tablist"` 는 쓰지 않는다 — 그 역할은 "같은 화면에서 패널만 바꾼다"는 뜻이다(09 F2).
 * - 숫자는 **모르는 동안 괄호째 생략**한다. `(0)` 을 잠깐 보였다가 숫자로 바꾸지 않는다(09 F6).
 *
 * @param {string} label `<nav aria-label>` — 화면 이름
 * @param {{key:string, label:string, href:string, count:number|null|undefined}[]} tabs
 * @param {string} current 선택된 탭 key
 */
export function PageTabs({ label, tabs, current }) {
  return (
    <nav className="page-tabs" aria-label={label}>
      {tabs.map((tab) => {
        const selected = tab.key === current;
        const known = typeof tab.count === "number";
        return (
          <Link
            key={tab.key}
            className={`page-tab${selected ? " selected" : ""}`}
            to={tab.href}
            aria-current={selected ? "page" : undefined}
            onClick={() => window.scrollTo(0, 0)}
          >
            {known ? `${tab.label} (${tab.count})` : tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
