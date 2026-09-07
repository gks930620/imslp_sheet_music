import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditionFormModal } from "./EditionFormModal.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../../test/apiMock.js";
import { adminEdition, editionFileUploadResponse } from "../../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../../test/text.js";

// 06_관리자_판본관리.md 화면 B — 판본 추가/수정 모달.
// props: mode("create"|"edit"), workId, edition(AdminEditionDTO|null), composer({nameKo, deathYear}), onClose, onSaved(savedEdition)
// 업로드: POST /api/admin/edition-files (multipart, 필드명 file) → 저장: POST /api/admin/works/{workId}/editions | PUT /api/admin/editions/{id}
const UPLOAD = /\/api\/admin\/edition-files$/;
const CREATE = /\/api\/admin\/works\/21\/editions$/;
const UPDATE = /\/api\/admin\/editions\/301$/;

const composer = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827 };

function renderModal(props = {}) {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  const utils = renderWithProviders(
    <EditionFormModal mode="create" workId={21} edition={null} composer={composer} onClose={onClose} onSaved={onSaved} {...props} />,
    { auth: "admin" },
  );
  return { ...utils, onClose, onSaved };
}

function fileInput() {
  return document.querySelector('input[type="file"]');
}

function pdfFile(name = "beethoven_op27-2.pdf", size = 1059957) {
  const file = new File(["%PDF-1.4"], name, { type: "application/pdf" });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("EditionFormModal — 제목·기본값", () => {
  // 화살표 본문을 블록으로: mockFetch 가 돌려주는 vi.fn 을 vitest 가 teardown 콜백으로 오해해 인자 없이 호출한다
  beforeEach(() => {
    mockFetch([]);
  });

  it("추가 모드: '판본 추가', 판정 기본값 '확인 중', 포함 범위 기본 '전곡'", () => {
    renderModal();
    expect(screen.getByRole("heading", { name: "판본 추가" })).toBeInTheDocument();
    expect(screen.getByLabelText("확인 중")).toBeChecked();
    expect(screen.getByLabelText("전곡")).toBeChecked();
    expect(screen.getByLabelText("전체 악보")).toBeInTheDocument();
    expect(screen.getByLabelText("파트보")).toBeInTheDocument();
    expect(screen.getByLabelText("편곡")).toBeInTheDocument();
    expect(screen.getByLabelText("특정 악장")).toBeInTheDocument();
    expect(screen.getByLabelText("자유 이용 가능")).toBeInTheDocument();
    expect(screen.getByLabelText("이용 제한")).toBeInTheDocument();
    expectText("PDF 파일을 끌어다 놓거나 눌러서 선택");
    expectText("PDF만, 100MB 이하");
    expect(fileInput()).toHaveAttribute("accept", "application/pdf");
  });

  it("수정 모드: '판본 수정', 기존 값이 채워진다", () => {
    renderModal({ mode: "edit", edition: adminEdition() });
    expect(screen.getByRole("heading", { name: "판본 수정" })).toBeInTheDocument();
    expect(screen.getByLabelText("자유 이용 가능")).toBeChecked();
    expect(screen.getByLabelText("판정 메모")).toHaveValue("작곡가 1827 사망, 편집자 Lebert 1884 사망 → 사후 70년 경과");
    expect(screen.getByLabelText("쪽수")).toHaveValue(12);
    expectText("12쪽 · 2.4MB");
    expect(screen.getByRole("button", { name: "교체" })).toBeInTheDocument();
  });

  it("작곡가 몰년 자동 표시: '작곡가 베토벤 · 1827 사망 (사후 70년: 1897 경과)'", () => {
    renderModal();
    expectText("작곡가 베토벤 · 1827 사망 (사후 70년: 1897 경과)");
  });

  it("사후 70년이 아직이면 '— 아직'", () => {
    renderModal({ composer: { ...composer, nameKo: "리게티", deathYear: 2006 } });
    expectText("작곡가 리게티 · 2006 사망 (사후 70년: 2076 — 아직)");
  });

  it("몰년 없으면 '작곡가 몰년이 없어요 → 작곡가 편집' 링크", () => {
    renderModal({ composer: { ...composer, deathYear: null } });
    expectText("작곡가 몰년이 없어요");
    expect(screen.getByRole("link", { name: /작곡가 편집/ })).toHaveAttribute("href", "/admin/composers/4");
  });

  it("판정 도움말 한 줄", () => {
    renderModal();
    expectText("작곡가와 편집자·편곡자 모두 사후 70년이 지나야 자유 이용 가능이에요. 불명확하면 '확인 중'으로 두세요");
  });
});

describe("EditionFormModal — PDF 업로드", () => {
  it("PDF 가 아닌 파일은 즉시 거부: 'PDF 파일만 올릴 수 있어요', 업로드 요청 없음", async () => {
    // accept="application/pdf" 를 user-event 가 적용하면 .txt 가 걸러져 change 가 안 난다.
    // 거부 문구는 앱이 내야 하므로 applyAccept:false 로 브라우저 필터를 끄고 앱 검증을 확인한다.
    const user = userEvent.setup({ applyAccept: false });
    mockFetch([{ url: UPLOAD, method: "POST", data: editionFileUploadResponse, status: 201 }]);
    renderModal();
    const bad = new File(["hello"], "notes.txt", { type: "text/plain" });
    await user.upload(fileInput(), bad);
    await findText("PDF 파일만 올릴 수 있어요");
    expect(findCalls(UPLOAD)).toHaveLength(0);
  });

  it("100MB 초과는 즉시 거부: '100MB 이하만 올릴 수 있어요 (현재 128MB)', 업로드 요청 없음", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: UPLOAD, method: "POST", data: editionFileUploadResponse, status: 201 }]);
    renderModal();
    await user.upload(fileInput(), pdfFile("big.pdf", 128 * 1024 * 1024));
    await waitFor(() => expect(document.body.textContent).toMatch(/100MB 이하만 올릴 수 있어요 \(현재 128(\.0)?MB\)/));
    expect(findCalls(UPLOAD)).toHaveLength(0);
  });

  it("PDF 를 고르면 즉시 multipart(file) 로 올리고, 쪽수·크기·미리보기를 자동으로 채운다", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: UPLOAD, method: "POST", data: editionFileUploadResponse, status: 201 }]);
    renderModal();
    await user.upload(fileInput(), pdfFile());
    await waitFor(() => expect(findCall(UPLOAD)).toBeDefined());
    const call = findCall(UPLOAD);
    expect(call.method).toBe("POST");
    expect(call.init.body).toBeInstanceOf(FormData);
    expect(call.init.body.get("file")).toBeInstanceOf(File);
    expect(call.init.body.get("file").name).toBe("beethoven_op27-2.pdf");
    await waitFor(() => expect(screen.getByLabelText("쪽수")).toHaveValue(14));
    expectText("beethoven_op27-2.pdf");
    expectText("14쪽 · 1.0MB");
    expect(screen.getByRole("img")).toHaveAttribute("src", "/uploads/preview-501.png");
  });

  it("미리보기 생성 실패(previewUrl null)면 '미리보기 준비 중', 저장은 가능", async () => {
    const user = userEvent.setup();
    mockFetch([
      { url: UPLOAD, method: "POST", data: { ...editionFileUploadResponse, previewFileId: null, previewUrl: null }, status: 201 },
      { url: CREATE, method: "POST", data: { ...adminEdition({ previewUrl: null }), workStatus: "UNKNOWN" }, status: 201 },
    ]);
    const { onSaved } = renderModal();
    await user.upload(fileInput(), pdfFile());
    await findText("미리보기 준비 중");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(findCall(CREATE).body.fileId).toBe(501);
    expect(findCall(CREATE).body.previewFileId).toBeNull();
  });

  it("업로드 서버 오류(400 PDF 아님) → 서버 문구 그대로", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: UPLOAD, method: "POST", status: 400, error: "BUSINESS_RULE_VIOLATION", message: "PDF 파일만 올릴 수 있어요" }]);
    renderModal();
    await user.upload(fileInput(), pdfFile("fake.pdf"));
    await findText("PDF 파일만 올릴 수 있어요");
  });
});

describe("EditionFormModal — 검증·저장", () => {
  it("scope=특정 악장인데 악장 번호가 없으면 '악장 번호를 입력해 주세요', 저장 요청 없음", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: CREATE, method: "POST", data: adminEdition(), status: 201 }]);
    renderModal();
    await user.click(screen.getByLabelText("특정 악장"));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("악장 번호를 입력해 주세요");
    expectText("입력을 확인해 주세요");
    expect(findCalls(CREATE)).toHaveLength(0);
  });

  it("FREE 로 바꾸고 메모가 비면 '판정 근거를 적어 주세요', 저장 요청 없음", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: CREATE, method: "POST", data: adminEdition(), status: 201 }]);
    renderModal();
    await user.click(screen.getByLabelText("자유 이용 가능"));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("판정 근거를 적어 주세요");
    expect(findCalls(CREATE)).toHaveLength(0);
  });

  it("RESTRICTED 도 메모 필수", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: CREATE, method: "POST", data: adminEdition(), status: 201 }]);
    renderModal();
    await user.click(screen.getByLabelText("이용 제한"));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("판정 근거를 적어 주세요");
    expect(findCalls(CREATE)).toHaveLength(0);
  });

  it("추가: 파일 없이 정보만 저장 → POST /api/admin/works/21/editions (fileId null, UNKNOWN 은 메모 선택), onSaved 호출", async () => {
    const user = userEvent.setup();
    const saved = { ...adminEdition({ id: 305, hasFile: false, pdfFileId: null }), workStatus: "PREPARING" };
    mockFetch([{ url: CREATE, method: "POST", data: saved, status: 201 }]);
    const { onSaved } = renderModal();
    await user.click(screen.getByLabelText("편곡"));
    await user.click(screen.getByLabelText("특정 악장"));
    await user.type(screen.getByLabelText("악장 번호"), "2");
    await user.type(screen.getByLabelText("쪽수"), "8");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
    const body = findCall(CREATE).body;
    expect(body.kind).toBe("ARRANGEMENT");
    expect(body.scope).toBe("MOVEMENT");
    expect(body.movementNumber).toBe(2);
    expect(body.pageCount).toBe(8);
    expect(body.koreaCopyright).toBe("UNKNOWN");
    expect(body.fileId).toBeNull();
  });

  it("추가: 업로드 후 저장하면 fileId·previewFileId·pageCount 가 실린다", async () => {
    const user = userEvent.setup();
    mockFetch([
      { url: UPLOAD, method: "POST", data: editionFileUploadResponse, status: 201 },
      { url: CREATE, method: "POST", data: { ...adminEdition(), workStatus: "READY" }, status: 201 },
    ]);
    renderModal();
    await user.upload(fileInput(), pdfFile());
    await waitFor(() => expect(screen.getByLabelText("쪽수")).toHaveValue(14));
    await user.click(screen.getByLabelText("자유 이용 가능"));
    await user.type(screen.getByLabelText("판정 메모"), "작곡가 1827 사망 → 사후 70년 경과");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(findCall(CREATE)).toBeDefined());
    const body = findCall(CREATE).body;
    expect(body.fileId).toBe(501);
    expect(body.previewFileId).toBe(502);
    expect(body.pageCount).toBe(14);
    expect(body.kind).toBe("COMPLETE_SCORE");
    expect(body.scope).toBe("COMPLETE");
    expect(body.movementNumber).toBeNull();
    expect(body.koreaCopyright).toBe("FREE");
    expect(body.copyrightNote).toBe("작곡가 1827 사망 → 사후 70년 경과");
  });

  it("수정: PUT /api/admin/editions/301", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: UPDATE, method: "PUT", data: { ...adminEdition(), workStatus: "READY" } }]);
    const { onSaved } = renderModal({ mode: "edit", edition: adminEdition() });
    await user.clear(screen.getByLabelText("쪽수"));
    await user.type(screen.getByLabelText("쪽수"), "13");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(findCall(UPDATE).method).toBe("PUT");
    expect(findCall(UPDATE).body.pageCount).toBe(13);
    expect(findCall(UPDATE).body.fileId).toBe(501);
  });

  it("업로드 중에는 저장 버튼 비활성 + '파일을 올리는 중이에요'", async () => {
    const user = userEvent.setup();
    let resolveUpload;
    mockFetch([
      {
        url: UPLOAD,
        method: "POST",
        handler: () => new Promise((resolve) => { resolveUpload = resolve; }),
      },
    ]);
    renderModal();
    await user.upload(fileInput(), pdfFile());
    await findText("파일을 올리는 중이에요");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
    expect(resolveUpload).toBeTypeOf("function");
  });

  it("서버 검증 실패(400 VALIDATION_ERROR errors[]) → 해당 필드 문구", async () => {
    const user = userEvent.setup();
    mockFetch([
      {
        url: CREATE,
        method: "POST",
        status: 400,
        error: "VALIDATION_ERROR",
        message: "입력값을 확인해 주세요",
        errors: [{ field: "imslpFileUrl", message: "IMSLP 주소가 아니에요", rejectedValue: "http://x" }],
      },
    ]);
    renderModal();
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("IMSLP 주소가 아니에요");
  });

  it("저장 실패(500) → '저장하지 못했어요. 잠시 후 다시 시도해 주세요'", async () => {
    const user = userEvent.setup();
    mockFetch([{ url: CREATE, method: "POST", status: 500, error: "INTERNAL_ERROR", message: "서버 오류" }]);
    renderModal();
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("저장하지 못했어요. 잠시 후 다시 시도해 주세요");
  });

  it("취소(변경 없음) → onClose", async () => {
    const user = userEvent.setup();
    mockFetch([]);
    const { onClose } = renderModal();
    await user.click(screen.getByRole("button", { name: "취소" }));
    expect(onClose).toHaveBeenCalled();
    expectNoText("작성 중인 내용이 사라져요");
  });

  it("취소(변경 있음) → ConfirmDialog '작성 중인 내용이 사라져요' → '나가기' 로 onClose", async () => {
    const user = userEvent.setup();
    mockFetch([]);
    const { onClose } = renderModal();
    await user.type(screen.getByLabelText("쪽수"), "3");
    await user.click(screen.getByRole("button", { name: "취소" }));
    await findText("작성 중인 내용이 사라져요");
    expect(onClose).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "나가기" }));
    expect(onClose).toHaveBeenCalled();
  });
});
