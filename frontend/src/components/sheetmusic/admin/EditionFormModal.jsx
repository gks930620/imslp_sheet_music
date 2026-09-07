import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ConfirmDialog } from "../../common/ConfirmDialog.jsx";
import { InlineAlert } from "../../common/InlineAlert.jsx";
import { callApi, uploadEditionFile } from "../../../lib/http.js";
import { formatFileSizeCompact } from "../../../lib/format.js";

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
const COPYRIGHT_TERM_YEARS = 70;

const KINDS = [
  { value: "COMPLETE_SCORE", label: "전체 악보" },
  { value: "PARTS", label: "파트보" },
  { value: "ARRANGEMENT", label: "편곡" },
];

const COPYRIGHTS = [
  { value: "FREE", label: "자유 이용 가능" },
  { value: "RESTRICTED", label: "이용 제한" },
  { value: "UNKNOWN", label: "확인 중" },
];

function initialForm(edition) {
  return {
    kind: edition?.kind ?? "COMPLETE_SCORE",
    scope: edition?.scope ?? "COMPLETE",
    movementNumber: edition?.movementNumber == null ? "" : String(edition.movementNumber),
    pageCount: edition?.pageCount == null ? "" : String(edition.pageCount),
    publisher: edition?.publisher ?? "",
    publishYear: edition?.publishYear == null ? "" : String(edition.publishYear),
    plateNumber: edition?.plateNumber ?? "",
    editor: edition?.editor ?? "",
    arranger: edition?.arranger ?? "",
    scanner: edition?.scanner ?? "",
    imslpFileUrl: edition?.imslpFileUrl ?? "",
    imslpCopyrightText: edition?.imslpCopyrightText ?? "",
    koreaCopyright: edition?.koreaCopyright ?? "UNKNOWN",
    copyrightNote: edition?.copyrightNote ?? "",
    ccLicenseName: edition?.ccLicenseName ?? "",
    ccAttribution: edition?.ccAttribution ?? "",
  };
}

function initialFile(edition) {
  if (!edition?.pdfFileId) return null;
  return {
    fileId: edition.pdfFileId,
    previewFileId: edition.previewFileId ?? null,
    fileName: edition.imslpOriginalFileName ?? "",
    fileSize: edition.fileSize ?? null,
    pageCount: edition.pageCount ?? null,
    previewUrl: edition.previewUrl ?? null,
  };
}

function numberOrNull(value) {
  const text = String(value ?? "").trim();
  return text === "" ? null : Number(text);
}

/** 06_관리자_판본관리.md 화면 B — 판본 추가/수정 모달 */
export function EditionFormModal({ mode = "create", workId, edition = null, composer = null, onClose, onSaved }) {
  const [form, setForm] = useState(() => initialForm(edition));
  const [file, setFile] = useState(() => initialFile(edition));
  const [dirty, setDirty] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [confirmClose, setConfirmClose] = useState(false);
  const fileInputRef = useRef(null);

  const setField = (name, value) => {
    setDirty(true);
    setForm((prev) => ({ ...prev, [name]: value }));
    setFieldErrors((prev) => ({ ...prev, [name]: undefined }));
  };

  const pickFile = async (picked) => {
    if (!picked) return;
    setUploadError("");
    const isPdf = picked.type === "application/pdf" || picked.name.toLowerCase().endsWith(".pdf");
    if (!isPdf) {
      setUploadError("PDF 파일만 올릴 수 있어요");
      return;
    }
    if (picked.size > MAX_UPLOAD_BYTES) {
      setUploadError(`100MB 이하만 올릴 수 있어요 (현재 ${formatFileSizeCompact(picked.size)})`);
      return;
    }

    setUploading(true);
    setDirty(true);
    try {
      const uploaded = await uploadEditionFile(picked);
      setFile(uploaded);
      if (uploaded?.pageCount) setForm((prev) => ({ ...prev, pageCount: String(uploaded.pageCount) }));
    } catch (error) {
      setUploadError(error?.message ?? "파일을 올리지 못했어요");
    } finally {
      setUploading(false);
    }
  };

  const validate = () => {
    const errors = {};
    if (form.scope === "MOVEMENT" && !numberOrNull(form.movementNumber)) {
      errors.movementNumber = "악장 번호를 입력해 주세요";
    }
    if ((form.koreaCopyright === "FREE" || form.koreaCopyright === "RESTRICTED") && !form.copyrightNote.trim()) {
      errors.copyrightNote = "판정 근거를 적어 주세요";
    }
    return errors;
  };

  const save = async () => {
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length) {
      setFormError("입력을 확인해 주세요");
      return;
    }

    setFormError("");
    setSaving(true);
    const payload = {
      fileId: file?.fileId ?? null,
      previewFileId: file?.previewFileId ?? null,
      kind: form.kind,
      scope: form.scope,
      movementNumber: form.scope === "MOVEMENT" ? numberOrNull(form.movementNumber) : null,
      pageCount: numberOrNull(form.pageCount),
      publisher: form.publisher || null,
      publishYear: numberOrNull(form.publishYear),
      plateNumber: form.plateNumber || null,
      editor: form.editor || null,
      arranger: form.arranger || null,
      scanner: form.scanner || null,
      imslpFileUrl: form.imslpFileUrl || null,
      imslpCopyrightText: form.imslpCopyrightText || null,
      koreaCopyright: form.koreaCopyright,
      copyrightNote: form.copyrightNote || null,
      ccLicenseName: form.ccLicenseName || null,
      ccAttribution: form.ccAttribution || null,
    };

    try {
      const url = mode === "edit" ? `/api/admin/editions/${edition.id}` : `/api/admin/works/${workId}/editions`;
      const result = await callApi(url, {
        method: mode === "edit" ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      onSaved?.(result.data);
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

  const requestClose = () => {
    if (dirty) setConfirmClose(true);
    else onClose?.();
  };

  const deathYear = composer?.deathYear ?? null;
  const publicDomainYear = deathYear ? deathYear + COPYRIGHT_TERM_YEARS : null;
  const passed = publicDomainYear ? publicDomainYear <= new Date().getFullYear() : false;

  return (
    <div className="modal-overlay">
      <div className="modal edition-form-modal" role="dialog" aria-modal="true" aria-label={mode === "edit" ? "판본 수정" : "판본 추가"}>
        <h2 className="modal-title">{mode === "edit" ? "판본 수정" : "판본 추가"}</h2>

        <div className="modal-body">
          {formError ? <InlineAlert variant="danger">{formError}</InlineAlert> : null}

          <div className="form-group file-upload-area">
            {file ? (
              <div className="edition-file">
                {file.previewUrl ? (
                  <img className="edition-file-preview" src={file.previewUrl} alt="첫 페이지 미리보기" />
                ) : (
                  <p className="edition-file-no-preview">미리보기 준비 중</p>
                )}
                {file.fileName ? <p className="edition-file-name">{file.fileName}</p> : null}
                <p className="edition-file-size">
                  {`${file.pageCount ?? form.pageCount}쪽 · ${formatFileSizeCompact(file.fileSize)}`}
                </p>
                <button className="btn btn-outline" type="button" onClick={() => fileInputRef.current?.click()}>
                  교체
                </button>
              </div>
            ) : (
              <label className="file-upload-label">
                <span className="material-icons">cloud_upload</span>
                <span>PDF 파일을 끌어다 놓거나 눌러서 선택</span>
                <span className="form-help">PDF만, 100MB 이하</span>
              </label>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf"
              onChange={(event) => pickFile(event.target.files?.[0])}
            />
            {uploading ? <p className="form-help">파일을 올리는 중이에요</p> : null}
            {uploadError ? <InlineAlert variant="danger">{uploadError}</InlineAlert> : null}
          </div>

          <fieldset className="form-group">
            <legend className="form-label required">판본 종류</legend>
            {KINDS.map((kind) => (
              <label key={kind.value} className="radio-item">
                <input
                  type="radio"
                  name="kind"
                  value={kind.value}
                  checked={form.kind === kind.value}
                  onChange={() => setField("kind", kind.value)}
                />
                <span>{kind.label}</span>
              </label>
            ))}
          </fieldset>

          <fieldset className="form-group">
            <legend className="form-label required">포함 범위</legend>
            <label className="radio-item">
              <input
                type="radio"
                name="scope"
                value="COMPLETE"
                checked={form.scope === "COMPLETE"}
                onChange={() => setField("scope", "COMPLETE")}
              />
              <span>전곡</span>
            </label>
            <label className="radio-item">
              <input
                type="radio"
                name="scope"
                value="MOVEMENT"
                checked={form.scope === "MOVEMENT"}
                onChange={() => setField("scope", "MOVEMENT")}
              />
              <span>특정 악장</span>
            </label>
            {form.scope === "MOVEMENT" ? (
              <span className="movement-number-field">
                <label className="form-label" htmlFor="movementNumber">
                  악장 번호
                </label>
                <input
                  id="movementNumber"
                  className="form-input"
                  type="number"
                  min="1"
                  value={form.movementNumber}
                  onChange={(event) => setField("movementNumber", event.target.value)}
                />
                {fieldErrors.movementNumber ? <span className="form-error">{fieldErrors.movementNumber}</span> : null}
              </span>
            ) : null}
          </fieldset>

          <div className="form-group">
            <label className="form-label" htmlFor="pageCount">
              쪽수
            </label>
            <input
              id="pageCount"
              className="form-input"
              type="number"
              min="1"
              value={form.pageCount}
              onChange={(event) => setField("pageCount", event.target.value)}
            />
            <span className="form-help">PDF에서 자동으로 채워요. 고칠 수 있어요</span>
          </div>

          <div className="form-group form-row">
            <span className="form-field">
              <label className="form-label" htmlFor="publisher">
                출판사
              </label>
              <input
                id="publisher"
                className="form-input"
                value={form.publisher}
                onChange={(event) => setField("publisher", event.target.value)}
              />
            </span>
            <span className="form-field">
              <label className="form-label" htmlFor="publishYear">
                출판 연도
              </label>
              <input
                id="publishYear"
                className="form-input"
                type="number"
                value={form.publishYear}
                onChange={(event) => setField("publishYear", event.target.value)}
              />
            </span>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="plateNumber">
              플레이트 번호
            </label>
            <input
              id="plateNumber"
              className="form-input"
              value={form.plateNumber}
              onChange={(event) => setField("plateNumber", event.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="editor">
              편집자
            </label>
            <input
              id="editor"
              className="form-input"
              value={form.editor}
              onChange={(event) => setField("editor", event.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="scanner">
              스캔 제공자
            </label>
            <input
              id="scanner"
              className="form-input"
              value={form.scanner}
              onChange={(event) => setField("scanner", event.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="imslpFileUrl">
              IMSLP 파일 페이지
            </label>
            <input
              id="imslpFileUrl"
              className="form-input"
              value={form.imslpFileUrl}
              onChange={(event) => setField("imslpFileUrl", event.target.value)}
            />
            {fieldErrors.imslpFileUrl ? <span className="form-error">{fieldErrors.imslpFileUrl}</span> : null}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="imslpCopyrightText">
              IMSLP 저작권 표기
            </label>
            <input
              id="imslpCopyrightText"
              className="form-input"
              value={form.imslpCopyrightText}
              onChange={(event) => setField("imslpCopyrightText", event.target.value)}
            />
          </div>

          <fieldset className="form-group copyright-group">
            <legend className="form-label required">한국 기준 저작권 판정</legend>
            {composer ? (
              <p className="copyright-composer">
                {deathYear ? (
                  `작곡가 ${composer.nameKo || composer.nameOriginal} · ${deathYear} 사망 (사후 70년: ${publicDomainYear} ${passed ? "경과" : "— 아직"})`
                ) : (
                  <>
                    {"작곡가 몰년이 없어요 → "}
                    <Link to={`/admin/composers/${composer.id}`}>작곡가 편집</Link>
                  </>
                )}
              </p>
            ) : null}

            {COPYRIGHTS.map((item) => (
              <label key={item.value} className="radio-item">
                <input
                  type="radio"
                  name="koreaCopyright"
                  value={item.value}
                  checked={form.koreaCopyright === item.value}
                  onChange={() => setField("koreaCopyright", item.value)}
                />
                <span>{item.label}</span>
              </label>
            ))}
            <p className="form-help">
              작곡가와 편집자·편곡자 모두 사후 70년이 지나야 자유 이용 가능이에요. 불명확하면 &apos;확인 중&apos;으로
              두세요
            </p>

            <label className="form-label" htmlFor="copyrightNote">
              판정 메모
            </label>
            <textarea
              id="copyrightNote"
              className="form-textarea"
              rows={2}
              placeholder="예: 작곡가 1827 사망, 편집자 1913 사망 → 사후 70년 경과"
              value={form.copyrightNote}
              onChange={(event) => setField("copyrightNote", event.target.value)}
            />
            {fieldErrors.copyrightNote ? <span className="form-error">{fieldErrors.copyrightNote}</span> : null}
          </fieldset>

          <div className="form-group form-row">
            <span className="form-field">
              <label className="form-label" htmlFor="ccLicenseName">
                CC 라이선스
              </label>
              <input
                id="ccLicenseName"
                className="form-input"
                value={form.ccLicenseName}
                onChange={(event) => setField("ccLicenseName", event.target.value)}
              />
            </span>
            <span className="form-field">
              <label className="form-label" htmlFor="ccAttribution">
                저작자
              </label>
              <input
                id="ccAttribution"
                className="form-input"
                value={form.ccAttribution}
                onChange={(event) => setField("ccAttribution", event.target.value)}
              />
            </span>
          </div>
          <p className="form-help">사용자 화면에 &quot;CC BY-SA 4.0 · 편집: ○○○&quot;로 보여요</p>
        </div>

        <div className="modal-actions">
          <button className="btn btn-secondary" type="button" onClick={requestClose}>
            취소
          </button>
          <button className="btn btn-primary" type="button" disabled={uploading || saving} onClick={save}>
            {saving ? "저장 중…" : "저장"}
          </button>
        </div>
      </div>

      {confirmClose ? (
        <ConfirmDialog
          title="작성 중인 내용이 사라져요"
          cancelLabel="계속 쓰기"
          confirmLabel="나가기"
          danger
          onCancel={() => setConfirmClose(false)}
          onConfirm={() => {
            setConfirmClose(false);
            onClose?.();
          }}
        />
      ) : null}
    </div>
  );
}
