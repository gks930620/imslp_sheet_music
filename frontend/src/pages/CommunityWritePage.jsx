import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { callApi, uploadFiles } from "../lib/http.js";
import { formatFileSize, getErrorMessage } from "../lib/format.js";

export function CommunityWritePage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [attachments, setAttachments] = useState([]);
  const [loading, setLoading] = useState(false);

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

    setLoading(true);
    try {
      const createResult = await callApi("/api/communities", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: title.trim(),
          content: content.trim(),
        }),
      });
      const communityId = createResult.data;

      if (attachments.length) {
        await uploadFiles(attachments, communityId, "ATTACHMENT");
      }

      navigate(`/community/detail?id=${communityId}`);
    } catch (e) {
      alert(getErrorMessage(e, "게시글 작성에 실패했습니다."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <section>
      <div className="page-header">
        <h1>
          <span className="material-icons">edit</span>
          게시글 작성
        </h1>
      </div>

      <form className="write-form" onSubmit={onSubmit}>
        <div className="form-group">
          <label className="form-label required" htmlFor="write-title">
            제목
          </label>
          <input
            id="write-title"
            className="form-input"
            maxLength={200}
            required
            type="text"
            placeholder="제목을 입력하세요"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>

        <div className="form-group">
          <label className="form-label required" htmlFor="write-content">
            내용
          </label>
          <textarea
            id="write-content"
            className="form-textarea"
            required
            rows={14}
            placeholder="내용을 입력하세요..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
          <div className="form-help">본문에 이미지를 삽입하려면 아래 버튼을 사용하세요.</div>
        </div>

        <div className="form-group">
          <button className="btn btn-secondary" type="button" onClick={() => document.getElementById("body-image-upload")?.click()}>
            <span className="material-icons">image</span>
            본문 이미지 업로드
          </button>
          <input id="body-image-upload" style={{ display: "none" }} type="file" accept="image/*" onChange={addBodyImage} />
        </div>

        <div className="form-group">
          <label className="form-label">첨부파일 (선택)</label>
          <div className="file-upload-area" onClick={() => document.getElementById("attach-file-upload")?.click()}>
            <span className="material-icons">attach_file</span>
            <p>클릭하여 파일을 첨부하세요</p>
            <p style={{ fontSize: 12, marginTop: 8 }}>모든 파일 형식 가능 (이미지, PDF, 문서 등)</p>
          </div>
          <input
            id="attach-file-upload"
            className="file-input"
            multiple
            type="file"
            onChange={(e) => setAttachments(Array.from(e.target.files ?? []))}
          />
        </div>

        {attachments.length > 0 && (
          <div className="uploaded-files">
            {attachments.map((file) => (
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
          <button className="btn btn-secondary" type="button" onClick={() => navigate("/community")}>
            <span className="material-icons">close</span>
            취소
          </button>
          <button className="btn btn-primary" disabled={loading} type="submit">
            <span className="material-icons">check</span>
            {loading ? "저장 중..." : "등록"}
          </button>
        </div>
      </form>
    </section>
  );
}
