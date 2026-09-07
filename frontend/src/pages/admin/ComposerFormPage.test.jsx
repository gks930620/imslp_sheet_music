import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ComposerFormPage } from "./ComposerFormPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import { adminComposer, adminComposerDetail, pageResponse } from "../../test/fixtures.js";
import { expectText, findText } from "../../test/text.js";

// 05_관리자_홈_및_작곡가곡관리.md 화면 C — /admin/composers/new, /admin/composers/:id
// 02_API §4-3(조회) / §4-4(POST·PUT) / §4-5(DELETE). 409 의 "보기" 는 §4-4 주석대로 ?q= 로 찾아 이동한다.
const DETAIL = /\/api\/admin\/composers\/4$/;
const COLLECTION = /\/api\/admin\/composers$/;
const SEARCH = /\/api\/admin\/composers\?/;
const ALIAS_PLACEHOLDER = "별칭 입력 후 Enter";

function renderNew(routes = []) {
  mockFetch([{ url: COLLECTION, method: "POST", data: adminComposerDetail(), status: 201 }, ...routes]);
  return renderWithProviders(<ComposerFormPage />, {
    route: "/admin/composers/new",
    path: "/admin/composers/new",
    auth: "admin",
  });
}

function renderEdit(detail = adminComposerDetail(), routes = []) {
  mockFetch([
    detail
      ? { url: DETAIL, method: "GET", data: detail }
      : { url: DETAIL, method: "GET", status: 404, error: "NOT_FOUND", message: "작곡가를 찾을 수 없어요" },
    { url: DETAIL, method: "PUT", data: adminComposerDetail() },
    { url: DETAIL, method: "DELETE", status: 204, raw: "" },
    ...routes,
  ]);
  return renderWithProviders(<ComposerFormPage />, {
    route: "/admin/composers/4",
    path: "/admin/composers/:id",
    auth: "admin",
  });
}

function methodCalls(method) {
  return findCalls(DETAIL).filter((call) => call.method === method);
}

async function fillRequired(user) {
  await user.type(screen.getByLabelText("한글 표기"), "베토벤");
  await user.type(screen.getByLabelText("원어 표기"), "Beethoven, Ludwig van");
}

async function waitLoaded() {
  await waitFor(() => expect(screen.getByLabelText("한글 표기")).toHaveValue("베토벤"));
}

describe("ComposerFormPage — 등록", () => {
  it("제목 '새 작곡가', 삭제 버튼 없음, 도움말 두 줄", () => {
    renderNew();
    expect(screen.getByRole("heading", { name: "새 작곡가" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "삭제" })).not.toBeInTheDocument();
    expectText("성, 이름 순으로 적어 주세요 (IMSLP 표기와 같게)");
    expectText("저작권 판정 근거로 쓰이니 입력을 권장해요");
    expect(screen.getByPlaceholderText(ALIAS_PLACEHOLDER)).toBeInTheDocument();
  });

  it("필수값이 비면 '입력을 확인해 주세요' + 필드별 문구, 저장 요청 없음", async () => {
    const user = userEvent.setup();
    renderNew();
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("입력을 확인해 주세요");
    expectText("한글 표기를 입력해 주세요");
    expectText("원어 표기를 입력해 주세요");
    expect(findCalls(COLLECTION)).toHaveLength(0);
  });

  it("몰년이 생년보다 앞서면 '몰년이 생년보다 앞서요', 저장 요청 없음", async () => {
    const user = userEvent.setup();
    renderNew();
    await fillRequired(user);
    await user.type(screen.getByLabelText("생년"), "1827");
    await user.type(screen.getByLabelText("몰년"), "1770");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("몰년이 생년보다 앞서요");
    expect(findCalls(COLLECTION)).toHaveLength(0);
  });

  it("IMSLP 주소가 아니면 'IMSLP 주소가 아니에요', 저장 요청 없음", async () => {
    const user = userEvent.setup();
    renderNew();
    await fillRequired(user);
    await user.type(screen.getByLabelText("IMSLP 작곡가 페이지"), "https://example.com/beethoven");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("IMSLP 주소가 아니에요");
    expect(findCalls(COLLECTION)).toHaveLength(0);
  });

  it("별칭 칩: Enter 로 추가, 같은 값은 늘지 않고, 칩 버튼으로 지운다", async () => {
    const user = userEvent.setup();
    renderNew();
    const alias = screen.getByPlaceholderText(ALIAS_PLACEHOLDER);
    await user.type(alias, "루트비히 판 베토벤{Enter}");
    expect(screen.getByRole("button", { name: "루트비히 판 베토벤 삭제" })).toBeInTheDocument();
    await user.type(alias, "루트비히 판 베토벤{Enter}");
    expect(screen.getAllByRole("button", { name: "루트비히 판 베토벤 삭제" })).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "루트비히 판 베토벤 삭제" }));
    expect(screen.queryByRole("button", { name: "루트비히 판 베토벤 삭제" })).not.toBeInTheDocument();
  });

  it("정상 저장 → POST 본문, 목록으로 이동, '저장했어요'", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderNew();
    await fillRequired(user);
    await user.type(screen.getByPlaceholderText(ALIAS_PLACEHOLDER), "루트비히 판 베토벤{Enter}");
    await user.type(screen.getByLabelText("생년"), "1770");
    await user.type(screen.getByLabelText("몰년"), "1827");
    await user.type(screen.getByLabelText("국적"), "독일");
    await user.type(
      screen.getByLabelText("IMSLP 작곡가 페이지"),
      "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van",
    );
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(findCall(COLLECTION)).toBeDefined());
    expect(findCall(COLLECTION).method).toBe("POST");
    expect(findCall(COLLECTION).body).toEqual({
      nameKo: "베토벤",
      nameOriginal: "Beethoven, Ludwig van",
      aliases: ["루트비히 판 베토벤"],
      birthYear: 1770,
      deathYear: 1827,
      nationality: "독일",
      imslpUrl: "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van",
    });
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/composers"));
    await findText("저장했어요");
  });

  it("409 중복 → '이미 등록된 작곡가예요' + '보기' 는 ?q= 로 찾은 첫 항목으로 이동", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderNew([
      { url: COLLECTION, method: "POST", status: 409, error: "DUPLICATE_RESOURCE", message: "이미 등록된 작곡가예요" },
      { url: SEARCH, method: "GET", data: pageResponse([adminComposer()]) },
    ]);
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("이미 등록된 작곡가예요");

    await user.click(screen.getByRole("button", { name: "보기" }));
    await waitFor(() => expect(findCall(SEARCH)).toBeDefined());
    expect(findCall(SEARCH).params.get("q")).toBe("Beethoven, Ludwig van");
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/composers/4"));
  });

  it("409 인데 ?q= 결과가 0건이면 '작곡가 목록에서 찾기' 링크", async () => {
    const user = userEvent.setup();
    renderNew([
      { url: COLLECTION, method: "POST", status: 409, error: "DUPLICATE_RESOURCE", message: "이미 등록된 작곡가예요" },
      { url: SEARCH, method: "GET", data: pageResponse([]) },
    ]);
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("이미 등록된 작곡가예요");
    await user.click(screen.getByRole("button", { name: "보기" }));
    await findText("작곡가 목록에서 찾기");
    expect(screen.getByRole("link", { name: "작곡가 목록에서 찾기" })).toHaveAttribute(
      "href",
      "/admin/composers?q=" + encodeURIComponent("Beethoven, Ludwig van"),
    );
  });

  it("저장 실패(500) → '저장하지 못했어요. 잠시 후 다시 시도해 주세요'", async () => {
    const user = userEvent.setup();
    renderNew([{ url: COLLECTION, method: "POST", status: 500, error: "INTERNAL_ERROR", message: "서버 오류" }]);
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("저장하지 못했어요. 잠시 후 다시 시도해 주세요");
  });
});

describe("ComposerFormPage — 수정", () => {
  it("기존 값이 채워지고 제목은 '작곡가 수정', 삭제 버튼이 있다", async () => {
    renderEdit();
    await waitLoaded();
    expect(screen.getByRole("heading", { name: "작곡가 수정" })).toBeInTheDocument();
    expect(screen.getByLabelText("원어 표기")).toHaveValue("Beethoven, Ludwig van");
    expect(screen.getByLabelText("생년")).toHaveValue("1770");
    expect(screen.getByLabelText("몰년")).toHaveValue("1827");
    expect(screen.getByLabelText("국적")).toHaveValue("독일");
    expect(screen.getByRole("button", { name: "루트비히 판 베토벤 삭제" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "삭제" })).toBeInTheDocument();
  });

  it("로딩 중에는 폼 스켈레톤", async () => {
    mockFetch([{ url: DETAIL, method: "GET", data: adminComposerDetail(), delay: 30 }]);
    renderWithProviders(<ComposerFormPage />, {
      route: "/admin/composers/4",
      path: "/admin/composers/:id",
      auth: "admin",
    });
    expect(document.querySelector(".skeleton-row")).toBeInTheDocument();
    await waitLoaded();
  });

  it("없는 id → '찾을 수 없는 페이지예요'", async () => {
    renderEdit(null);
    await findText("찾을 수 없는 페이지예요");
  });

  it("저장 → PUT 으로 보내고 목록으로 이동", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderEdit();
    await waitLoaded();
    await user.clear(screen.getByLabelText("국적"));
    await user.type(screen.getByLabelText("국적"), "오스트리아");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(methodCalls("PUT")).toHaveLength(1));
    expect(methodCalls("PUT")[0].body.nationality).toBe("오스트리아");
    expect(methodCalls("PUT")[0].body.aliases).toEqual(["루트비히 판 베토벤"]);
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/composers"));
  });

  it("곡 0개면 확인 대화상자 → 삭제 → 목록 이동 + '삭제했어요' (브라우저 confirm 안 씀)", async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);
    const { getLocation } = renderEdit(adminComposerDetail({ workCount: 0 }));
    await waitLoaded();
    await user.click(screen.getByRole("button", { name: "삭제" }));
    const dialog = screen.getByRole("dialog", { name: "이 작곡가를 삭제할까요?" });
    expect(confirmSpy).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole("button", { name: "삭제" }));
    await waitFor(() => expect(methodCalls("DELETE")).toHaveLength(1));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/composers"));
    await findText("삭제했어요");
    confirmSpy.mockRestore();
  });

  it("곡이 있으면 확인 창 없이 안내만 띄우고 삭제 요청을 보내지 않는다", async () => {
    const user = userEvent.setup();
    renderEdit(adminComposerDetail({ workCount: 12 }));
    await waitLoaded();
    await user.click(screen.getByRole("button", { name: "삭제" }));
    await findText("곡 12개가 있어 삭제할 수 없어요. 곡을 먼저 옮기거나 지우세요");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(methodCalls("DELETE")).toHaveLength(0);
  });

  it("취소: 바꾼 게 없으면 바로 목록, 바꿨으면 '작성 중인 내용이 사라져요' 확인", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderEdit();
    await waitLoaded();
    await user.type(screen.getByLabelText("국적"), "!");
    await user.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.getByRole("dialog", { name: "작성 중인 내용이 사라져요" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 작성" }));
    expect(getLocation().pathname).toBe("/admin/composers/4");

    await user.click(screen.getByRole("button", { name: "취소" }));
    await user.click(screen.getByRole("button", { name: "나가기" }));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/composers"));
  });
});
