import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/Pagination.jsx";
import { ConfirmDialog } from "../../components/common/ConfirmDialog.jsx";
import { EmptyState } from "../../components/common/EmptyState.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { showToast } from "../../components/common/Toast.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useComposerOptions } from "../../hooks/useComposerOptions.js";
import { useDebouncedSearchInput } from "../../hooks/useDebouncedSearchInput.js";
import { callApi } from "../../lib/http.js";
import { applyAdminFilter, hasAdminFilter } from "../../lib/adminQuery.js";
import { buildWorkQuery, withPage } from "../../lib/workQuery.js";
import { formatEditionKind, formatEditionScope } from "../../lib/format.js";

const FILTER_KEYS = ["q", "composerId"];
const QUERY_KEYS = [...FILTER_KEYS, "page"];
const SKELETON_ROWS = [0, 1, 2, 3, 4, 5, 6, 7];
const NOTE_REQUIRED = "판정 근거를 적어 주세요";

const VERDICTS = [
  { value: "FREE", label: "자유 이용 가능" },
  { value: "RESTRICTED", label: "이용 제한" },
];

function verdictLabel(value) {
  return VERDICTS.find((verdict) => verdict.value === value)?.label ?? "";
}

/** 06_관리자_판본관리.md 화면 C — /admin/copyright (02_API §5-8·5-9·5-10) */
export function CopyrightPendingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const query = buildWorkQuery(searchParams, QUERY_KEYS);
  const hasFilter = hasAdminFilter(searchParams, FILTER_KEYS);

  const list = useApiResource(() => callApi(`/api/admin/copyright/pending?${query}`).then((result) => result.data), {
    deps: [query],
  });
  const { options: composerOptions } = useComposerOptions();

  const [text, changeText] = useDebouncedSearchInput(q, (next) =>
    setSearchParams(applyAdminFilter(searchParams, { q: next.trim() })),
  );

  const [verdicts, setVerdicts] = useState({});
  const [notes, setNotes] = useState({});
  const [rowErrors, setRowErrors] = useState({});
  const [savingIds, setSavingIds] = useState([]);
  const [selected, setSelected] = useState([]);
  const [bulkVerdict, setBulkVerdict] = useState("");
  const [bulkNote, setBulkNote] = useState("");
  const [bulkConfirm, setBulkConfirm] = useState(false);
  const [bulkWarning, setBulkWarning] = useState("");

  const data = list.data;
  const page = data?.editions;
  const rows = page?.content ?? [];
  const isEmpty = Boolean(data) && rows.length === 0;
  const isEmptyAll = isEmpty && !hasFilter;

  /** 판정한 행은 목록에서 빼고 대기 건수를 줄인다 (다시 불러오지 않는다) */
  const removeRows = (editionIds) => {
    if (!data) return;
    list.setData({
      ...data,
      unfilteredTotal: Math.max(0, data.unfilteredTotal - editionIds.length),
      editions: {
        ...page,
        content: rows.filter((row) => !editionIds.includes(row.editionId)),
        totalElements: Math.max(0, (page.totalElements ?? 0) - editionIds.length),
      },
    });
    setSelected((prev) => prev.filter((editionId) => !editionIds.includes(editionId)));
  };

  const judge = async (editionId) => {
    setRowErrors((prev) => ({ ...prev, [editionId]: false }));
    setSavingIds((prev) => [...prev, editionId]);
    try {
      await callApi(`/api/admin/editions/${editionId}/copyright`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ koreaCopyright: verdicts[editionId], copyrightNote: notes[editionId] }),
      });
      removeRows([editionId]);
      showToast("판정했어요");
    } catch {
      setRowErrors((prev) => ({ ...prev, [editionId]: true }));
    } finally {
      setSavingIds((prev) => prev.filter((id) => id !== editionId));
    }
  };

  const applyBulk = async () => {
    setBulkConfirm(false);
    setBulkWarning("");
    const editionIds = rows.map((row) => row.editionId).filter((editionId) => selected.includes(editionId));
    try {
      const result = await callApi("/api/admin/editions/copyright/bulk", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ editionIds, koreaCopyright: bulkVerdict, copyrightNote: bulkNote }),
      });
      const succeeded = result.data?.succeeded ?? [];
      const failed = result.data?.failed ?? [];
      removeRows(succeeded);
      if (failed.length) {
        setBulkWarning(`${succeeded.length}개 판정, ${failed.length}개 실패 — 실패한 행은 목록에 남아 있어요`);
      } else {
        showToast(`${succeeded.length}개를 판정했어요`);
        setBulkNote("");
        setBulkVerdict("");
      }
    } catch {
      setBulkWarning("판정을 저장하지 못했어요 — 다시 시도");
    }
  };

  const toggleAll = (checked) => setSelected(checked ? rows.map((row) => row.editionId) : []);

  const toggleRow = (editionId, checked) =>
    setSelected((prev) => (checked ? [...prev, editionId] : prev.filter((id) => id !== editionId)));

  return (
    <div className="copyright-pending">
      <nav className="breadcrumb">
        <Link to="/admin">관리</Link>
        <span> › </span>
        <span>저작권 판정 대기함</span>
      </nav>

      <div className="page-header">
        <h1>
          <span className="material-icons">gavel</span>
          저작권 판정 대기함
        </h1>
        {data ? <p className="admin-list-count">{`확인 중인 판본 ${data.unfilteredTotal}개`}</p> : null}
      </div>

      <div className="admin-list-filters">
        <span className="search-bar search-bar-compact admin-list-search">
          <span className="search-bar-field">
            <span className="material-icons search-bar-icon" aria-hidden="true">
              search
            </span>
            <input
              className="search-bar-input"
              type="search"
              value={text}
              placeholder="곡 이름, 작곡가"
              aria-label="곡 이름, 작곡가"
              onChange={(event) => changeText(event.target.value)}
            />
          </span>
        </span>

        <span className="admin-filter" data-label="작곡가">
          <select
            className="filter-select"
            aria-label="작곡가"
            value={searchParams.get("composerId") ?? ""}
            onChange={(event) => setSearchParams(applyAdminFilter(searchParams, { composerId: event.target.value }))}
          >
            <option value="">전체</option>
            {composerOptions.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </span>
      </div>

      {selected.length ? (
        <div className="copyright-bulk-bar">
          <span className="copyright-bulk-count">{`선택 ${selected.length}개`}</span>
          <span className="copyright-bulk-verdicts" role="radiogroup" aria-label="일괄 판정">
            {VERDICTS.map((verdict) => (
              <label key={verdict.value} className="radio-item">
                <input
                  type="radio"
                  name="bulkVerdict"
                  checked={bulkVerdict === verdict.value}
                  onChange={() => setBulkVerdict(verdict.value)}
                />
                <span>{verdict.label}</span>
              </label>
            ))}
          </span>
          <input
            className="form-input"
            aria-label="일괄 판정 메모"
            value={bulkNote}
            onChange={(event) => setBulkNote(event.target.value)}
          />
          <button
            className="btn btn-primary"
            type="button"
            disabled={!bulkVerdict || !bulkNote.trim()}
            onClick={() => setBulkConfirm(true)}
          >
            {`선택한 ${selected.length}개에 적용`}
          </button>
          {bulkVerdict && !bulkNote.trim() ? <span className="form-error">{NOTE_REQUIRED}</span> : null}
        </div>
      ) : null}

      {bulkWarning ? <InlineAlert variant="warning">{bulkWarning}</InlineAlert> : null}

      {list.error ? (
        <ErrorState onRetry={list.reload} />
      ) : !data ? (
        <div className="skeleton-list" aria-hidden="true">
          {SKELETON_ROWS.map((index) => (
            <div key={index} className="skeleton-row" />
          ))}
        </div>
      ) : isEmptyAll ? (
        <EmptyState icon="task_alt" title="확인 중인 판본이 없어요" description="모든 판본의 판정이 끝났어요" />
      ) : isEmpty ? (
        <EmptyState icon="filter_alt_off" title="이 조건에 맞는 판본이 없어요">
          <button className="btn btn-text" type="button" onClick={() => setSearchParams(new URLSearchParams())}>
            필터 해제
          </button>
        </EmptyState>
      ) : (
        <>
          <div className="data-table copyright-table">
            <div className="data-table-head">
              <span>
                <input
                  type="checkbox"
                  aria-label="전체 선택"
                  checked={rows.length > 0 && selected.length === rows.length}
                  onChange={(event) => toggleAll(event.target.checked)}
                />
              </span>
              <span>곡</span>
              <span>작곡가</span>
              <span>판본</span>
              <span>편집자</span>
              <span>IMSLP 표기</span>
              <span>판정</span>
            </div>

            {rows.map((row) => {
              const verdict = verdicts[row.editionId] ?? "";
              const note = notes[row.editionId] ?? "";
              const saving = savingIds.includes(row.editionId);
              return (
                <div key={row.editionId} className="data-table-row" data-testid={`pending-row-${row.editionId}`}>
                  <span>
                    <input
                      type="checkbox"
                      aria-label={`${row.work.titleKo || row.work.titleOriginal} 선택`}
                      checked={selected.includes(row.editionId)}
                      onChange={(event) => toggleRow(row.editionId, event.target.checked)}
                    />
                  </span>

                  <span className="pending-work">
                    <Link to={`/admin/works/${row.work.id}`} target="_blank" rel="noreferrer">
                      {row.work.titleKo || row.work.titleOriginal}
                      <span className="material-icons" aria-hidden="true">
                        open_in_new
                      </span>
                    </Link>
                  </span>

                  <span className="pending-composer">
                    {`${row.composer.nameKo || row.composer.nameOriginal} (${
                      row.composer.deathYear ?? "몰년 없음"
                    })`}
                  </span>

                  <span className="pending-edition">{`${formatEditionKind(row.kind)} · ${formatEditionScope(row)}`}</span>

                  <span className="pending-editor">{row.editor || "–"}</span>

                  <span className="pending-imslp">
                    {row.imslpCopyrightText || "–"}
                    {row.imslpFileUrl ? (
                      <a href={row.imslpFileUrl} target="_blank" rel="noreferrer">
                        IMSLP 파일
                        <span className="material-icons" aria-hidden="true">
                          open_in_new
                        </span>
                      </a>
                    ) : null}
                  </span>

                  <span className="pending-judge">
                    {VERDICTS.map((item) => (
                      <button
                        key={item.value}
                        className={`btn btn-outline${verdict === item.value ? " btn-outline-active" : ""}`}
                        type="button"
                        aria-pressed={verdict === item.value}
                        onClick={() => setVerdicts((prev) => ({ ...prev, [row.editionId]: item.value }))}
                      >
                        {item.label}
                      </button>
                    ))}
                    <input
                      className="form-input"
                      aria-label="판정 메모"
                      value={note}
                      onChange={(event) =>
                        setNotes((prev) => ({ ...prev, [row.editionId]: event.target.value }))
                      }
                    />
                    <button
                      className="btn btn-primary"
                      type="button"
                      disabled={!verdict || !note.trim() || saving}
                      onClick={() => judge(row.editionId)}
                    >
                      저장
                    </button>
                    {verdict && !note.trim() ? <span className="form-error">{NOTE_REQUIRED}</span> : null}
                    {rowErrors[row.editionId] ? (
                      <InlineAlert variant="danger">판정을 저장하지 못했어요 — 다시 시도</InlineAlert>
                    ) : null}
                  </span>
                </div>
              );
            })}
          </div>

          <Pagination
            page={page.page}
            totalPages={page.totalPages}
            onChange={(next) => setSearchParams(withPage(searchParams, next))}
          />
        </>
      )}

      {bulkConfirm ? (
        <ConfirmDialog
          title={`${selected.length}개 판본을 '${verdictLabel(bulkVerdict)}'으로 판정할까요?`}
          description={bulkNote}
          confirmLabel="판정"
          cancelLabel="취소"
          onCancel={() => setBulkConfirm(false)}
          onConfirm={applyBulk}
        />
      ) : null}
    </div>
  );
}
