import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ConfirmDialog } from "../../components/common/ConfirmDialog.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { NotFoundView } from "../../components/common/NotFoundView.jsx";
import { showToast } from "../../components/common/Toast.jsx";
import { EditionListSection } from "../../components/sheetmusic/admin/EditionListSection.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { useComposerOptions } from "../../hooks/useComposerOptions.js";
import { callApi } from "../../lib/http.js";

const SKELETON_ROWS = [0, 1, 2, 3, 4, 5, 6, 7];

const EMPTY_FORM = {
  titleKo: "",
  titleOriginal: "",
  level: "",
  compositionYear: "",
  musicalKey: "",
  movements: "",
  movementPageGuide: "",
  collectionGuide: "",
  imslpUrl: "",
  hidden: false,
};

const LEVELS = [
  { value: "BEGINNER", label: "입문", guide: "바이엘 수준" },
  { value: "ELEMENTARY", label: "초급", guide: "체르니 30 수준" },
  { value: "INTERMEDIATE", label: "중급", guide: "체르니 40·소나티네 수준" },
  { value: "ADVANCED", label: "고급", guide: "체르니 50 이상·연주회 레퍼토리" },
  { value: "", label: "미정", guide: "" },
];

/** 02_API §4-7 missing 코드 → 화면 문구 */
const MISSING_LABELS = {
  TITLE_KO: "한국어 제목",
  ALIAS: "별칭",
  LEVEL: "난이도",
  RECOMMENDED_EDITION: "추천 판본",
};

const TITLE_KO_HELP = "비워 두면 사용자에겐 원어 제목이 보이고 상태는 '보완 필요'가 돼요";
const MOVEMENT_GUIDE_HELP = '사용자 곡 상세에 "악장 안내: … (추천 판본 기준)"으로 보여요';
/**
 * 라벨 "수록곡 안내" 만으로는 무엇을 적는 칸인지 알 수 없다. 게다가 이 값 하나가 사용자 화면을 바꾼다
 * (02 §2-2-1 COLLECTION — 난이도·쪽수에 "(전곡 기준)"이 붙는다). 그 사실이 관리자가 이 칸을 채울
 * 유일한 동기라 뒷절을 함께 적는다. (기획 §3 F5-2 · §12-2)
 */
const COLLECTION_GUIDE_HELP =
  '여러 곡·여러 악장이 한 PDF 에 들어 있는 곡이면 적어 주세요 — 이 줄이 있어야 사용자 화면에 "(전곡 기준)"이 붙어요';
const HIDDEN_HELP = "숨기면 검색·인기곡·작곡가 어디에도 나오지 않아요";

/** 상세 응답 → 폼 값. 이탈 방지(05-E)의 '바뀐 게 있나' 비교 기준도 이걸로 만든다. */
function formFromDetail(detail) {
  if (!detail) return { ...EMPTY_FORM };
  return {
    titleKo: detail.titleKo ?? "",
    titleOriginal: detail.titleOriginal ?? "",
    level: detail.level ?? "",
    compositionYear: detail.compositionYear ?? "",
    musicalKey: detail.musicalKey ?? "",
    movements: detail.movements ?? "",
    movementPageGuide: detail.movementPageGuide ?? "",
    collectionGuide: detail.collectionGuide ?? "",
    imslpUrl: detail.imslpUrl ?? "",
    hidden: Boolean(detail.hidden),
  };
}

function composerLabel(composer) {
  if (!composer) return "";
  return composer.nameKo ? `${composer.nameKo} (${composer.nameOriginal})` : composer.nameOriginal;
}

function TagChips({ values, onRemove, shaking }) {
  return values.map((value) => (
    <span key={value} className={`tag-chip${shaking === value ? " shaking" : ""}`}>
      {value}
      <button className="tag-chip-remove" type="button" aria-label={`${value} 삭제`} onClick={() => onRemove(value)}>
        <span className="material-icons" aria-hidden="true">
          close
        </span>
      </button>
    </span>
  ));
}

/** 05_관리자 화면 E — /admin/works/new, /admin/works/:id (02_API §4-7·4-8·4-9·4-10) */
export function WorkFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const detail = useApiResource(() => callApi(`/api/admin/works/${id}`).then((result) => result.data), {
    deps: [id],
    enabled: isEdit,
  });
  const { options: composerOptions } = useComposerOptions();

  const [form, setForm] = useState(EMPTY_FORM);
  const [composerId, setComposerId] = useState(null);
  const [composerText, setComposerText] = useState("");
  const [composerOpen, setComposerOpen] = useState(false);
  const [catalogNumbers, setCatalogNumbers] = useState([]);
  const [catalogText, setCatalogText] = useState("");
  const [aliases, setAliases] = useState([]);
  const [aliasText, setAliasText] = useState("");
  const [shaking, setShaking] = useState("");
  const [overlaps, setOverlaps] = useState({});
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [pendingLeaveTo, setPendingLeaveTo] = useState("");

  const loaded = detail.data;
  useEffect(() => {
    if (!loaded) return;
    setForm(formFromDetail(loaded));
    setComposerId(loaded.composer?.id ?? null);
    setComposerText(composerLabel(loaded.composer));
    setCatalogNumbers(loaded.catalogNumbers ?? []);
    setAliases(loaded.aliases ?? []);
  }, [loaded]);

  // 05-E 이탈 방지 — 저장된 값(없으면 빈 폼)과 지금 폼을 견준다(03 §11-1: 화면 안의 이동만 막는다)
  const savedSnapshot = useMemo(
    () =>
      JSON.stringify({
        form: formFromDetail(loaded),
        composerId: loaded?.composer?.id ?? null,
        catalogNumbers: loaded?.catalogNumbers ?? [],
        aliases: loaded?.aliases ?? [],
      }),
    [loaded],
  );
  const dirty = JSON.stringify({ form, composerId, catalogNumbers, aliases }) !== savedSnapshot;

  if (isEdit && detail.error) {
    return detail.error.status === 404 ? <NotFoundView /> : <ErrorState onRetry={detail.reload} />;
  }

  const setField = (name, value) => {
    setForm((prev) => ({ ...prev, [name]: value }));
    setFieldErrors((prev) => ({ ...prev, [name]: undefined }));
  };

  const addCatalogNumber = () => {
    const value = catalogText.trim();
    if (!value) return;
    if (catalogNumbers.includes(value)) {
      setShaking(value);
      return;
    }
    setCatalogNumbers((prev) => [...prev, value]);
    setCatalogText("");
  };

  const addAlias = async () => {
    const value = aliasText.trim();
    if (!value) return;
    if (aliases.includes(value)) {
      setShaking(value);
      return;
    }
    setAliases((prev) => [...prev, value]);
    setAliasText("");

    // 02_API §4-10 — 다른 곡과 겹치는 별칭은 경고만 (저장은 가능)
    const params = new URLSearchParams({ alias: value });
    if (isEdit) params.set("excludeWorkId", String(id));
    try {
      const result = await callApi(`/api/admin/works/aliases/overlap?${params.toString()}`);
      const count = result.data?.overlapCount ?? 0;
      if (count > 0) setOverlaps((prev) => ({ ...prev, [value]: count }));
    } catch {
      /* 겹침 경고는 보조 정보 — 실패해도 저장을 막지 않는다 */
    }
  };

  const removeAlias = (value) => {
    setAliases((prev) => prev.filter((alias) => alias !== value));
    setOverlaps((prev) => ({ ...prev, [value]: 0 }));
  };

  const validate = () => {
    const errors = {};
    if (!composerId) errors.composerId = "작곡가를 골라 주세요";
    if (!form.titleOriginal.trim()) errors.titleOriginal = "원어 제목을 입력해 주세요";
    return errors;
  };

  const buildPayload = (overrides = {}) => ({
    composerId,
    titleKo: form.titleKo.trim() || null,
    titleOriginal: form.titleOriginal.trim(),
    catalogNumbers,
    aliases,
    level: form.level || null,
    compositionYear: form.compositionYear.trim() || null,
    musicalKey: form.musicalKey.trim() || null,
    movements: form.movements.trim() || null,
    movementPageGuide: form.movementPageGuide.trim() || null,
    collectionGuide: form.collectionGuide.trim() || null,
    imslpUrl: form.imslpUrl.trim() || null,
    hidden: form.hidden,
    ...overrides,
  });

  const submit = async (payload) => {
    setSaving(true);
    setFormError("");
    try {
      const result = await callApi(isEdit ? `/api/admin/works/${id}` : "/api/admin/works", {
        method: isEdit ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const saved = result.data;
      const opened = loaded?.status !== "READY" && saved?.status === "READY";
      showToast(opened ? "저장했어요 — 사용자 화면에서 다운로드가 열렸어요" : "저장했어요");
      if (isEdit) detail.setData(saved);
      else navigate(`/admin/works/${saved.id}`);
    } catch (error) {
      if (error?.errorCode === "VALIDATION_ERROR" && error.errors?.length) {
        const next = {};
        error.errors.forEach((item) => {
          next[item.field] = item.message;
        });
        setFieldErrors(next);
        setFormError("입력을 확인해 주세요");
      } else {
        setFormError("저장하지 못했어요. 잠시 후 다시 시도해 주세요");
      }
    } finally {
      setSaving(false);
    }
  };

  const save = async () => {
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length) {
      setFormError("입력을 확인해 주세요");
      return;
    }
    await submit(buildPayload());
  };

  const hideInsteadOfDelete = async () => {
    setConfirmDelete(false);
    setForm((prev) => ({ ...prev, hidden: true }));
    await submit(buildPayload({ hidden: true }));
  };

  const remove = async () => {
    setConfirmDelete(false);
    try {
      await callApi(`/api/admin/works/${id}`, { method: "DELETE" });
      showToast("삭제했어요");
      navigate("/admin/works");
    } catch {
      setFormError("삭제하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  /** 화면 안의 이동(브레드크럼·작곡가 편집)만 가로챈다 — 03 §11-1 */
  const guardLeave = (to) => (event) => {
    if (!dirty) return;
    event.preventDefault();
    setPendingLeaveTo(to);
  };

  const loading = isEdit && !loaded;
  const editions = loaded?.editions ?? [];
  const hasDownloadHistory = Boolean(loaded?.hasDownloadHistory);
  const missingLabels = (loaded?.missing ?? []).map((code) => MISSING_LABELS[code]).filter(Boolean);
  const filteredComposers = composerOptions.filter((option) =>
    composerText.trim() ? composerLabel(option).toLowerCase().includes(composerText.trim().toLowerCase()) : true,
  );

  return (
    <div className="admin-form-page admin-work-form">
      <nav className="breadcrumb">
        <Link to="/admin">관리</Link>
        <span> › </span>
        <Link to="/admin/works" onClick={guardLeave("/admin/works")}>
          곡 관리
        </Link>
        <span> › </span>
        <span>{isEdit ? loaded?.titleKo || loaded?.titleOriginal || "" : "새 곡"}</span>
      </nav>

      <div className="page-header">
        <h1>{isEdit ? "곡 수정" : "새 곡"}</h1>
        {isEdit ? (
          <div className="page-header-actions">
            <Link className="btn btn-text" to={`/works/${id}`} target="_blank" rel="noreferrer">
              사용자 화면에서 보기
              <span className="material-icons" aria-hidden="true">
                open_in_new
              </span>
            </Link>
            <button className="btn btn-danger" type="button" onClick={() => setConfirmDelete(true)}>
              삭제
            </button>
          </div>
        ) : null}
      </div>

      {loaded?.needsWork ? (
        <InlineAlert variant="warning">
          {`빠진 것: ${missingLabels.join(", ")}`}
          <span className="status-badge badge-needs-work">보완 필요</span>
        </InlineAlert>
      ) : null}

      {loaded?.composer?.nameKoMissing ? (
        <InlineAlert variant="warning">
          작곡가 한글 표기가 없어요
          <Link
            className="btn btn-text"
            to={`/admin/composers/${loaded.composer.id}`}
            onClick={guardLeave(`/admin/composers/${loaded.composer.id}`)}
          >
            작곡가 편집
          </Link>
        </InlineAlert>
      ) : null}

      {formError ? <InlineAlert variant="danger">{formError}</InlineAlert> : null}

      {loading ? (
        <div className="skeleton-list" aria-hidden="true">
          {SKELETON_ROWS.map((index) => (
            <div key={index} className="skeleton-row" />
          ))}
        </div>
      ) : (
        <>
          <section className="panel admin-form">
            <h2 className="panel-title">곡 정보</h2>

            <div className="form-group composer-picker">
              <label className="form-label required" htmlFor="composerInput">
                작곡가
              </label>
              <input
                id="composerInput"
                className={`form-input${fieldErrors.composerId ? " form-input-error" : ""}`}
                autoComplete="off"
                value={composerText}
                onChange={(event) => {
                  setComposerText(event.target.value);
                  setComposerId(null);
                  setComposerOpen(true);
                  setFieldErrors((prev) => ({ ...prev, composerId: undefined }));
                }}
                onFocus={() => setComposerOpen(true)}
              />
              {fieldErrors.composerId ? <span className="form-error">{fieldErrors.composerId}</span> : null}
              {composerOpen ? (
                <ul className="composer-options" role="listbox" aria-label="작곡가 선택">
                  {filteredComposers.map((option) => (
                    <li
                      key={option.id}
                      role="option"
                      aria-selected={option.id === composerId}
                      className="composer-option"
                      onClick={() => {
                        setComposerId(option.id);
                        setComposerText(composerLabel(option));
                        setComposerOpen(false);
                      }}
                    >
                      {composerLabel(option)}
                    </li>
                  ))}
                  <li className="composer-option-add">
                    <Link to="/admin/composers/new" target="_blank" rel="noreferrer">
                      <span className="material-icons" aria-hidden="true">
                        add
                      </span>
                      새 작곡가 등록
                    </Link>
                  </li>
                </ul>
              ) : null}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="titleKo">
                한국어 대표 제목
              </label>
              <input
                id="titleKo"
                className="form-input"
                value={form.titleKo}
                onChange={(event) => setField("titleKo", event.target.value)}
              />
              <span className="form-help">{TITLE_KO_HELP}</span>
            </div>

            <div className="form-group">
              <label className="form-label required" htmlFor="titleOriginal">
                원어 제목
              </label>
              <input
                id="titleOriginal"
                className={`form-input${fieldErrors.titleOriginal ? " form-input-error" : ""}`}
                value={form.titleOriginal}
                onChange={(event) => setField("titleOriginal", event.target.value)}
              />
              {fieldErrors.titleOriginal ? <span className="form-error">{fieldErrors.titleOriginal}</span> : null}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="catalogInput">
                작품번호
              </label>
              <div className="tag-input">
                <TagChips
                  values={catalogNumbers}
                  shaking={shaking}
                  onRemove={(value) => setCatalogNumbers((prev) => prev.filter((item) => item !== value))}
                />
                <input
                  id="catalogInput"
                  className="tag-input-field"
                  value={catalogText}
                  placeholder="예: Op.27 No.2 — 입력 후 Enter"
                  onChange={(event) => {
                    setCatalogText(event.target.value);
                    setShaking("");
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== "Enter") return;
                    event.preventDefault();
                    addCatalogNumber();
                  }}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="aliasInput">
                별칭
              </label>
              <div className="tag-input">
                <TagChips values={aliases} shaking={shaking} onRemove={removeAlias} />
                <input
                  id="aliasInput"
                  className="tag-input-field"
                  value={aliasText}
                  placeholder="예: 월광 — 입력 후 Enter"
                  onChange={(event) => {
                    setAliasText(event.target.value);
                    setShaking("");
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== "Enter") return;
                    event.preventDefault();
                    addAlias();
                  }}
                />
              </div>
              {Object.entries(overlaps)
                .filter(([alias, count]) => count > 0 && aliases.includes(alias))
                .map(([alias, count]) => (
                  <InlineAlert key={alias} variant="info">
                    {`'${alias}'은 다른 곡 ${count}개에도 있어요`}
                  </InlineAlert>
                ))}
            </div>

            <fieldset className="form-group level-group">
              <legend className="form-label">난이도</legend>
              {LEVELS.map((level) => (
                <span key={level.label} className="level-radio">
                  <label className="radio-item">
                    <input
                      type="radio"
                      name="level"
                      value={level.value}
                      checked={form.level === level.value}
                      onChange={() => setField("level", level.value)}
                    />
                    <span>{level.label}</span>
                  </label>
                  {level.guide ? <span className="form-help">{level.guide}</span> : null}
                </span>
              ))}
            </fieldset>

            <div className="form-group form-row">
              <span className="form-field">
                <label className="form-label" htmlFor="compositionYear">
                  작곡 연도
                </label>
                <input
                  id="compositionYear"
                  className="form-input"
                  type="text"
                  inputMode="numeric"
                  value={form.compositionYear}
                  onChange={(event) => setField("compositionYear", event.target.value)}
                />
              </span>
              <span className="form-field">
                <label className="form-label" htmlFor="musicalKey">
                  조성
                </label>
                <input
                  id="musicalKey"
                  className="form-input"
                  value={form.musicalKey}
                  onChange={(event) => setField("musicalKey", event.target.value)}
                />
              </span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="movements">
                악장 구성
              </label>
              <input
                id="movements"
                className="form-input"
                value={form.movements}
                onChange={(event) => setField("movements", event.target.value)}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="movementPageGuide">
                악장 페이지 안내
              </label>
              <input
                id="movementPageGuide"
                className="form-input"
                value={form.movementPageGuide}
                onChange={(event) => setField("movementPageGuide", event.target.value)}
              />
              <span className="form-help">{MOVEMENT_GUIDE_HELP}</span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="collectionGuide">
                수록곡 안내
              </label>
              <textarea
                id="collectionGuide"
                className="form-textarea"
                rows={3}
                value={form.collectionGuide}
                onChange={(event) => setField("collectionGuide", event.target.value)}
              />
              <span className="form-help">{COLLECTION_GUIDE_HELP}</span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="workImslpUrl">
                IMSLP 작품 페이지
              </label>
              <input
                id="workImslpUrl"
                className="form-input"
                value={form.imslpUrl}
                onChange={(event) => setField("imslpUrl", event.target.value)}
              />
            </div>

            <fieldset className="form-group">
              <legend className="form-label">사용자 화면 노출</legend>
              <label className="radio-item">
                <input
                  type="radio"
                  name="hidden"
                  checked={!form.hidden}
                  onChange={() => setField("hidden", false)}
                />
                <span>보임</span>
              </label>
              <label className="radio-item">
                <input type="radio" name="hidden" checked={form.hidden} onChange={() => setField("hidden", true)} />
                <span>숨김</span>
              </label>
              {form.hidden ? <span className="form-help">{HIDDEN_HELP}</span> : null}
              {loaded?.hiddenReason ? (
                <InlineAlert variant="warning">
                  {`수집 시 "${loaded.hiddenReason}"로 숨김 처리됨 — 확인 후 보임으로 바꿔 주세요`}
                </InlineAlert>
              ) : null}
            </fieldset>

            <div className="form-actions">
              <button className="btn btn-primary" type="button" disabled={saving} onClick={save}>
                {saving ? "저장 중…" : "저장"}
              </button>
            </div>
          </section>

          {isEdit ? (
            <EditionListSection
              workId={loaded.id}
              editions={editions}
              recommendedEditionId={loaded.recommendedEditionId}
              candidateEditionId={loaded.candidateEditionId}
              recommendationReviewed={loaded.recommendationReviewed}
              composer={loaded.composer}
              onChanged={detail.reload}
              onToast={showToast}
            />
          ) : (
            <p className="form-help admin-editions-hint">곡을 저장하면 판본을 추가할 수 있어요</p>
          )}
        </>
      )}

      {pendingLeaveTo ? (
        <ConfirmDialog
          title="저장하지 않은 변경이 있어요"
          cancelLabel="계속 작성"
          confirmLabel="나가기"
          onCancel={() => setPendingLeaveTo("")}
          onConfirm={() => {
            const to = pendingLeaveTo;
            setPendingLeaveTo("");
            navigate(to);
          }}
        />
      ) : null}

      {confirmDelete ? (
        <ConfirmDialog
          title={editions.length > 0 ? `판본 ${editions.length}개와 파일이 함께 지워져요. 삭제할까요?` : "이 곡을 삭제할까요?"}
          description={
            hasDownloadHistory ? "이 곡은 다운로드 기록이 있어요. 삭제 대신 '숨김'을 권해요" : undefined
          }
          cancelLabel="취소"
          extraLabel={hasDownloadHistory ? "숨김으로 바꾸기" : ""}
          confirmLabel={hasDownloadHistory ? "그래도 삭제" : "삭제"}
          danger
          onCancel={() => setConfirmDelete(false)}
          onExtra={hideInsteadOfDelete}
          onConfirm={remove}
        />
      ) : null}
    </div>
  );
}
