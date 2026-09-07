import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ConfirmDialog } from "../../components/common/ConfirmDialog.jsx";
import { ErrorState } from "../../components/common/ErrorState.jsx";
import { InlineAlert } from "../../components/common/InlineAlert.jsx";
import { NotFoundView } from "../../components/common/NotFoundView.jsx";
import { showToast } from "../../components/common/Toast.jsx";
import { useApiResource } from "../../hooks/useApiResource.js";
import { callApi } from "../../lib/http.js";

const IMSLP_PREFIX = "https://imslp.org/";
const EMPTY_FORM = { nameKo: "", nameOriginal: "", birthYear: "", deathYear: "", nationality: "", imslpUrl: "" };
const SKELETON_ROWS = [0, 1, 2, 3, 4, 5];

function numberOrNull(value) {
  const text = String(value ?? "").trim();
  return text === "" ? null : Number(text);
}

/** 05_관리자 화면 C — /admin/composers/new, /admin/composers/:id (02_API §4-3·4-4·4-5) */
export function ComposerFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const detail = useApiResource(() => callApi(`/api/admin/composers/${id}`).then((result) => result.data), {
    deps: [id],
    enabled: isEdit,
  });

  const [form, setForm] = useState(EMPTY_FORM);
  const [aliases, setAliases] = useState([]);
  const [aliasText, setAliasText] = useState("");
  const [shakingAlias, setShakingAlias] = useState("");
  const [dirty, setDirty] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [duplicate, setDuplicate] = useState(null);
  const [saving, setSaving] = useState(false);
  const [deleteBlocked, setDeleteBlocked] = useState("");
  const [confirming, setConfirming] = useState("");

  const loaded = detail.data;
  useEffect(() => {
    if (!loaded) return;
    setForm({
      nameKo: loaded.nameKo ?? "",
      nameOriginal: loaded.nameOriginal ?? "",
      birthYear: loaded.birthYear == null ? "" : String(loaded.birthYear),
      deathYear: loaded.deathYear == null ? "" : String(loaded.deathYear),
      nationality: loaded.nationality ?? "",
      imslpUrl: loaded.imslpUrl ?? "",
    });
    setAliases(loaded.aliases ?? []);
  }, [loaded]);

  if (isEdit && detail.error) {
    return detail.error.status === 404 ? <NotFoundView /> : <ErrorState onRetry={detail.reload} />;
  }

  const setField = (name, value) => {
    setDirty(true);
    setForm((prev) => ({ ...prev, [name]: value }));
    setFieldErrors((prev) => ({ ...prev, [name]: undefined }));
  };

  const addAlias = () => {
    const value = aliasText.trim();
    if (!value) return;
    if (aliases.includes(value)) {
      setShakingAlias(value);
      return;
    }
    setDirty(true);
    setAliases((prev) => [...prev, value]);
    setAliasText("");
  };

  const removeAlias = (value) => {
    setDirty(true);
    setAliases((prev) => prev.filter((alias) => alias !== value));
  };

  const validate = () => {
    const errors = {};
    if (!form.nameKo.trim()) errors.nameKo = "한글 표기를 입력해 주세요";
    if (!form.nameOriginal.trim()) errors.nameOriginal = "원어 표기를 입력해 주세요";
    const birthYear = numberOrNull(form.birthYear);
    const deathYear = numberOrNull(form.deathYear);
    if (birthYear && deathYear && deathYear < birthYear) errors.deathYear = "몰년이 생년보다 앞서요";
    if (form.imslpUrl.trim() && !form.imslpUrl.trim().startsWith(IMSLP_PREFIX)) {
      errors.imslpUrl = "IMSLP 주소가 아니에요";
    }
    return errors;
  };

  const save = async () => {
    const errors = validate();
    setFieldErrors(errors);
    setDuplicate(null);
    if (Object.keys(errors).length) {
      setFormError("입력을 확인해 주세요");
      return;
    }

    setFormError("");
    setSaving(true);
    const payload = {
      nameKo: form.nameKo.trim(),
      nameOriginal: form.nameOriginal.trim(),
      aliases,
      birthYear: numberOrNull(form.birthYear),
      deathYear: numberOrNull(form.deathYear),
      nationality: form.nationality.trim() || null,
      imslpUrl: form.imslpUrl.trim() || null,
    };

    try {
      await callApi(isEdit ? `/api/admin/composers/${id}` : "/api/admin/composers", {
        method: isEdit ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      showToast("저장했어요");
      navigate("/admin/composers");
    } catch (error) {
      if (error?.status === 409) {
        setDuplicate({ message: error.message ?? "이미 등록된 작곡가예요", looking: false, notFound: false });
      } else if (error?.errorCode === "VALIDATION_ERROR" && error.errors?.length) {
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

  /** 409 의 "보기" — ErrorResponse 에 id 가 없어 원어 표기로 찾아 첫 항목으로 간다 (02_API §4-4) */
  const openDuplicate = async () => {
    const keyword = form.nameOriginal.trim();
    setDuplicate((prev) => ({ ...prev, looking: true, notFound: false }));
    try {
      const result = await callApi(`/api/admin/composers?q=${encodeURIComponent(keyword)}`);
      const found = result.data?.content?.[0];
      if (found) navigate(`/admin/composers/${found.id}`);
      else setDuplicate((prev) => ({ ...prev, looking: false, notFound: true }));
    } catch {
      setDuplicate((prev) => ({ ...prev, looking: false, notFound: true }));
    }
  };

  const remove = async () => {
    setConfirming("");
    try {
      await callApi(`/api/admin/composers/${id}`, { method: "DELETE" });
      showToast("삭제했어요");
      navigate("/admin/composers");
    } catch (error) {
      setFormError(error?.message ?? "삭제하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  const requestDelete = () => {
    const workCount = loaded?.workCount ?? 0;
    if (workCount > 0) {
      setDeleteBlocked(`곡 ${workCount}개가 있어 삭제할 수 없어요. 곡을 먼저 옮기거나 지우세요`);
      return;
    }
    setDeleteBlocked("");
    setConfirming("delete");
  };

  const requestCancel = () => {
    if (dirty) setConfirming("leave");
    else navigate("/admin/composers");
  };

  const loading = isEdit && !loaded;

  return (
    <div className="admin-form-page">
      <nav className="breadcrumb">
        <Link to="/admin">관리</Link>
        <span> › </span>
        <Link to="/admin/composers">작곡가 관리</Link>
        <span> › </span>
        <span>{isEdit ? loaded?.nameKo || loaded?.nameOriginal || "" : "새 작곡가"}</span>
      </nav>

      <div className="page-header">
        <h1>{isEdit ? "작곡가 수정" : "새 작곡가"}</h1>
        {isEdit ? (
          <button className="btn btn-danger" type="button" onClick={requestDelete}>
            삭제
          </button>
        ) : null}
      </div>

      {deleteBlocked ? <InlineAlert variant="danger">{deleteBlocked}</InlineAlert> : null}
      {formError ? <InlineAlert variant="danger">{formError}</InlineAlert> : null}

      {loading ? (
        <div className="skeleton-list" aria-hidden="true">
          {SKELETON_ROWS.map((index) => (
            <div key={index} className="skeleton-row" />
          ))}
        </div>
      ) : (
        <section className="panel admin-form">
          <div className="form-group">
            <label className="form-label required" htmlFor="nameKo">
              한글 표기
            </label>
            <input
              id="nameKo"
              className={`form-input${fieldErrors.nameKo ? " form-input-error" : ""}`}
              value={form.nameKo}
              onChange={(event) => setField("nameKo", event.target.value)}
            />
            {fieldErrors.nameKo ? <span className="form-error">{fieldErrors.nameKo}</span> : null}
          </div>

          <div className="form-group">
            <label className="form-label required" htmlFor="nameOriginal">
              원어 표기
            </label>
            <input
              id="nameOriginal"
              className={`form-input${fieldErrors.nameOriginal ? " form-input-error" : ""}`}
              value={form.nameOriginal}
              onChange={(event) => setField("nameOriginal", event.target.value)}
            />
            <span className="form-help">성, 이름 순으로 적어 주세요 (IMSLP 표기와 같게)</span>
            {fieldErrors.nameOriginal ? <span className="form-error">{fieldErrors.nameOriginal}</span> : null}
            {duplicate ? (
              <InlineAlert variant="danger">
                {duplicate.message}
                {duplicate.notFound ? (
                  <Link className="btn btn-text" to={`/admin/composers?q=${encodeURIComponent(form.nameOriginal.trim())}`}>
                    작곡가 목록에서 찾기
                  </Link>
                ) : (
                  <button className="btn btn-text" type="button" disabled={duplicate.looking} onClick={openDuplicate}>
                    보기
                  </button>
                )}
              </InlineAlert>
            ) : null}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="aliasInput">
              검색용 별칭
            </label>
            <div className="tag-input">
              {aliases.map((alias) => (
                <span key={alias} className={`tag-chip${shakingAlias === alias ? " shaking" : ""}`}>
                  {alias}
                  <button
                    className="tag-chip-remove"
                    type="button"
                    aria-label={`${alias} 삭제`}
                    onClick={() => removeAlias(alias)}
                  >
                    <span className="material-icons" aria-hidden="true">
                      close
                    </span>
                  </button>
                </span>
              ))}
              <input
                id="aliasInput"
                className="tag-input-field"
                value={aliasText}
                placeholder="별칭 입력 후 Enter"
                onChange={(event) => {
                  setAliasText(event.target.value);
                  setShakingAlias("");
                }}
                onKeyDown={(event) => {
                  if (event.key !== "Enter") return;
                  event.preventDefault();
                  addAlias();
                }}
              />
            </div>
          </div>

          <div className="form-group form-row">
            <span className="form-field">
              <label className="form-label" htmlFor="birthYear">
                생년
              </label>
              <input
                id="birthYear"
                className="form-input"
                type="text"
                inputMode="numeric"
                maxLength={4}
                value={form.birthYear}
                onChange={(event) => setField("birthYear", event.target.value)}
              />
            </span>
            <span className="form-row-dash" aria-hidden="true">
              –
            </span>
            <span className="form-field">
              <label className="form-label" htmlFor="deathYear">
                몰년
              </label>
              <input
                id="deathYear"
                className={`form-input${fieldErrors.deathYear ? " form-input-error" : ""}`}
                type="text"
                inputMode="numeric"
                maxLength={4}
                value={form.deathYear}
                onChange={(event) => setField("deathYear", event.target.value)}
              />
            </span>
          </div>
          <span className="form-help">저작권 판정 근거로 쓰이니 입력을 권장해요</span>
          {fieldErrors.deathYear ? <span className="form-error">{fieldErrors.deathYear}</span> : null}

          <div className="form-group">
            <label className="form-label" htmlFor="nationality">
              국적
            </label>
            <input
              id="nationality"
              className="form-input"
              value={form.nationality}
              onChange={(event) => setField("nationality", event.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="imslpUrl">
              IMSLP 작곡가 페이지
            </label>
            <input
              id="imslpUrl"
              className={`form-input${fieldErrors.imslpUrl ? " form-input-error" : ""}`}
              value={form.imslpUrl}
              onChange={(event) => setField("imslpUrl", event.target.value)}
            />
            {fieldErrors.imslpUrl ? <span className="form-error">{fieldErrors.imslpUrl}</span> : null}
          </div>

          <div className="form-actions">
            <button className="btn btn-secondary" type="button" onClick={requestCancel}>
              취소
            </button>
            <button className="btn btn-primary" type="button" disabled={saving} onClick={save}>
              {saving ? "저장 중…" : "저장"}
            </button>
          </div>
        </section>
      )}

      {confirming === "delete" ? (
        <ConfirmDialog
          title="이 작곡가를 삭제할까요?"
          confirmLabel="삭제"
          cancelLabel="취소"
          danger
          onCancel={() => setConfirming("")}
          onConfirm={remove}
        />
      ) : null}

      {confirming === "leave" ? (
        <ConfirmDialog
          title="작성 중인 내용이 사라져요"
          confirmLabel="나가기"
          cancelLabel="계속 작성"
          danger
          onCancel={() => setConfirming("")}
          onConfirm={() => {
            setConfirming("");
            navigate("/admin/composers");
          }}
        />
      ) : null}
    </div>
  );
}
