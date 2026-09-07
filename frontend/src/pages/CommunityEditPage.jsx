import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { authFetch, callApi, callPublicApi, toApiError, uploadFiles } from "../lib/http.js";
import { formatFileSize, getErrorMessage } from "../lib/format.js";
import { useAuth } from "../context/AuthContext.jsx";

export function CommunityEditPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const communityId = searchParams.get("id");
  const { user } = useAuth();

  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [existingFiles, setExistingFiles] = useState([]);
  const [newFiles, setNewFiles] = useState([]);
  const [deletedFileIds, setDeletedFileIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const toggleDeleteFile = (fileId) => {
    setDeletedFileIds((prev) => (prev.includes(fileId) ? prev.filter((id) => id !== fileId) : [...prev, fileId]));
  };

  const addBodyImage = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    if (!file.type.startsWith("image/")) {
      alert("이미지 파일만 업로드 가능합니다.");
      return;
    }

    try {
      const uploaded = await uploadFiles([file], 0, "IMAGES");
      const imageUrl = uploaded[0];
      if (imageUrl) {
        setContent((prev) => `${prev}\n<p><img src="${imageUrl}" alt="" /></p>`);
      }
    } catch (e) {
      alert(getErrorMessage(e, "본문 이미지 업로드에 실패했습니다."));
    }
  };

  const loadData = async () => {
    if (!communityId) {
      alert("잘못된 접근입니다.");
      navigate("/community");
      return;
    }

    setLoading(true);
    try {
      const [postResult, filesResult] = await Promise.all([
        callPublicApi(`/api/communities/${communityId}`),
        callPublicApi(`/api/files?refId=${communityId}&refType=COMMUNITY&usage=ATTACHMENT`),
      ]);

      const postData = postResult.data;
      if (user && postData.username !== user.username) {
        alert("본인 글만 수정할 수 있습니다.");
        navigate(`/community/detail?id=${communityId}`);
        return;
      }

      setTitle(postData.title ?? "");
      setContent(postData.content ?? "");
      setExistingFiles(filesResult.data ?? []);
    } catch (e) {
      alert(getErrorMessage(e, "게시글 정보를 불러오지 못했습니다."));
      navigate("/community");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user) {
      loadData();
    }
  }, [communityId, user]);

  const onSubmit = async (event) => {
    event.preventDefault();
    if (!title.trim()) {
      alert("제목을 입력하세요.");
      return;
    }
    if (!content.trim()) {
      alert("내용을 입력하세요.");
      return;
    }

    setSaving(true);
    try {
      await callApi(`/api/communities/${communityId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: title.trim(),
          content: content.trim(),
        }),
      });

      for (const fileId of deletedFileIds) {
        const deleteResponse = await authFetch(`/api/files/${fileId}`, { method: "DELETE" });
        if (!deleteResponse.ok) {
          throw await toApiError(deleteResponse);
        }
      }

      if (newFiles.length) {
        await uploadFiles(newFiles, communityId, "ATTACHMENT");
      }

      navigate(`/community/detail?id=${communityId}`);
    } catch (e) {
      alert(getErrorMessage(e, "게시글 수정에 실패했습니다."));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="empty-state" style={{ paddingTop: 40, paddingBottom: 40 }}>
        <div style={{ fontSize: 14 }}>로딩 중...</div>
      </div>
    );
  }

  return (
    <section>
      <div className="page-header">
        <h1>
          <span className="material-icons">edit</span>
          게시글 수정
        </h1>
      </div>

      <form className="write-form" onSubmit={onSubmit}>
        <div className="form-group">
          <label className="form-label required" htmlFor="edit-title">
            제목
          </label>
          <input
            id="edit-title"
            className="form-input"
            maxLength={200}
            required
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>

        <div className="form-group">
          <label className="form-label required" htmlFor="edit-content">
            내용
          </label>
          <textarea
            id="edit-content"
            className="form-textarea"
            required
            rows={14}
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
          <div className="form-help">최소 5자 이상 작성해주세요.</div>
        </div>

        <div className="form-group">
          <button className="btn btn-secondary" type="button" onClick={() => document.getElementById("edit-body-image-upload")?.click()}>
            <span className="material-icons">image</span>
            본문 이미지 업로드
          </button>
          <input id="edit-body-image-upload" style={{ display: "none" }} type="file" accept="image/*" onChange={addBodyImage} />
        </div>

        <div className="form-group">
          <label className="form-label">첨부파일 관리</label>

          {existingFiles.length === 0 && <p className="muted-text">기존 첨부파일이 없습니다.</p>}
          {existingFiles.map((file) => {
            const marked = deletedFileIds.includes(file.fileId);
            return (
              <div className={`existing-file ${marked ? "file-deleted" : ""}`} key={file.fileId}>
                <div className="uploaded-file-info">
                  <span className="material-icons">insert_drive_file</span>
                  <span>
                    {file.originalFileName} ({formatFileSize(file.fileSize)}) {marked ? "- 삭제 예정" : ""}
                  </span>
                </div>
                <button className="remove-file-btn" type="button" onClick={() => toggleDeleteFile(file.fileId)}>
                  <span className="material-icons">{marked ? "undo" : "delete"}</span>
                </button>
              </div>
            );
          })}
        </div>

        <div className="form-group">
          <div className="file-upload-area" onClick={() => document.getElementById("edit-attach-upload")?.click()}>
            <span className="material-icons">attach_file</span>
            <p>클릭하여 파일을 추가하세요</p>
            <p style={{ fontSize: 12, marginTop: 8 }}>모든 파일 형식 가능 (이미지, PDF, 문서 등)</p>
          </div>
          <input
            id="edit-attach-upload"
            className="file-input"
            multiple
            type="file"
            onChange={(e) => setNewFiles(Array.from(e.target.files ?? []))}
          />
        </div>

        {newFiles.length > 0 && (
          <div className="uploaded-files">
            {newFiles.map((file) => (
              <div className="uploaded-file" key={`${file.name}-${file.lastModified}`}>
                <div className="uploaded-file-info">
                  <span className="material-icons">insert_drive_file</span>
                  <span>
                    {file.name} ({formatFileSize(file.size)})
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="form-actions">
          <button className="btn btn-secondary" type="button" onClick={() => navigate(`/community/detail?id=${communityId}`)}>
            <span className="material-icons">close</span>
            취소
          </button>
          <button className="btn btn-primary" disabled={saving} type="submit">
            <span className="material-icons">check</span>
            {saving ? "저장 중..." : "수정 완료"}
          </button>
        </div>
      </form>
    </section>
  );
}
