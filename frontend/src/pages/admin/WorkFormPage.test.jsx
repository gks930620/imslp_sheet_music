import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WorkFormPage } from "./WorkFormPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCall, findCalls } from "../../test/apiMock.js";
import {
  adminComposers,
  adminEditionRecommended,
  adminWorkDetail,
  pageResponse,
} from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 05_관리자_홈_및_작곡가곡관리.md 화면 E — /admin/works/new, /admin/works/:id (곡 정보 영역)
// 02_API §4-7(조회) / §4-8(POST·PUT) / §4-9(DELETE) / §4-10(별칭 겹침) + 작곡가 선택지 §4-2(size=200)
// 아래쪽 판본 영역은 EditionListSection (06-A, 별도 테스트)
const DETAIL = /\/api\/admin\/works\/21$/;
const COLLECTION = /\/api\/admin\/works$/;
const OVERLAP = /\/api\/admin\/works\/aliases\/overlap/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const CATALOG_PLACEHOLDER = "예: Op.27 No.2 — 입력 후 Enter";
const ALIAS_PLACEHOLDER = "예: 월광 — 입력 후 Enter";

function baseRoutes() {
  return [
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: OVERLAP, method: "GET", data: { alias: "월광", overlapCount: 0 } },
  ];
}

function renderEdit(detail = adminWorkDetail(), routes = []) {
  mockFetch([
    ...baseRoutes(),
    detail
      ? { url: DETAIL, method: "GET", data: detail }
      : { url: DETAIL, method: "GET", status: 404, error: "NOT_FOUND", message: "곡을 찾을 수 없어요" },
    { url: DETAIL, method: "PUT", data: adminWorkDetail() },
    { url: DETAIL, method: "DELETE", status: 204, raw: "" },
    ...routes,
  ]);
  return renderWithProviders(<WorkFormPage />, { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" });
}

function renderNew(routes = []) {
  mockFetch([
    ...baseRoutes(),
    { url: COLLECTION, method: "POST", data: adminWorkDetail(), status: 201 },
    ...routes,
  ]);
  return renderWithProviders(<WorkFormPage />, { route: "/admin/works/new", path: "/admin/works/new", auth: "admin" });
}

function detailCalls(method) {
  return findCalls(DETAIL).filter((call) => call.method === method);
}

async function waitLoaded() {
  await waitFor(() => expect(screen.getByLabelText("원어 제목")).toHaveValue("Piano Sonata No.14, Op.27 No.2"));
}

// 05-E 의 곡 삭제 버튼과 06-A 의 판본 행 삭제 버튼은 같은 화면에 있고 정의서상 이름이 둘 다 "삭제" 다.
// 곡 삭제는 머리말(page-header) 영역의 버튼이므로 그 영역으로 좁혀 찾는다(판본 행은 EditionListSection 테스트가 행 스코프로 검증).
function workDeleteButton() {
  const header = screen.getByRole("heading", { name: /곡 수정|새 곡/ }).closest(".page-header");
  return within(header).getByRole("button", { name: "삭제" });
}

describe("WorkFormPage — 머리말·알림", () => {
  it("제목 '곡 수정', 브레드크럼, '사용자 화면에서 보기'(새 탭)·'삭제'", async () => {
    renderEdit();
    await waitLoaded();
    expect(screen.getByRole("heading", { name: "곡 수정" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "관리" })).toHaveAttribute("href", "/admin");
    expect(screen.getByRole("link", { name: "곡 관리" })).toHaveAttribute("href", "/admin/works");
    const view = screen.getByRole("link", { name: /사용자 화면에서 보기/ });
    expect(view).toHaveAttribute("href", "/works/21");
    expect(view).toHaveAttribute("target", "_blank");
    expect(workDeleteButton()).toBeInTheDocument();
  });

  it("보완 필요면 '빠진 것: …' 을 빠진 항목만 나열하고 '보완 필요' 뱃지를 단다", async () => {
    renderEdit(
      adminWorkDetail({
        titleKo: null,
        level: null,
        status: "PREPARING",
        needsWork: true,
        missing: ["TITLE_KO", "LEVEL", "RECOMMENDED_EDITION"],
        recommendedEditionId: null,
      }),
    );
    await waitLoaded();
    expectText("빠진 것: 한국어 제목, 난이도, 추천 판본");
    expectText("보완 필요");
    expectNoText("별칭,");
  });

  it("보완 필요가 아니면 '빠진 것' 알림이 없다", async () => {
    renderEdit();
    await waitLoaded();
    expectNoText("빠진 것");
  });

  it("작곡가 한글 표기가 없으면 안내 + '작곡가 편집' 링크", async () => {
    renderEdit(
      adminWorkDetail({
        composer: { id: 4, nameKo: null, nameOriginal: "Beethoven, Ludwig van", deathYear: 1827, nameKoMissing: true },
      }),
    );
    await waitLoaded();
    expectText("작곡가 한글 표기가 없어요");
    expect(screen.getByRole("link", { name: "작곡가 편집" })).toHaveAttribute("href", "/admin/composers/4");
  });

  it("없는 id → '찾을 수 없는 페이지예요'", async () => {
    renderEdit(null);
    await findText("찾을 수 없는 페이지예요");
  });

  it("로딩 중에는 폼 스켈레톤", async () => {
    mockFetch([...baseRoutes(), { url: DETAIL, method: "GET", data: adminWorkDetail(), delay: 30 }]);
    renderWithProviders(<WorkFormPage />, { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" });
    expect(document.querySelector(".skeleton-row")).toBeInTheDocument();
    await waitLoaded();
  });
});

describe("WorkFormPage — 곡 정보 입력", () => {
  it("기존 값이 채워진다 (작곡가는 '한글 (원어)' 표기)", async () => {
    renderEdit();
    await waitLoaded();
    expect(screen.getByLabelText("작곡가")).toHaveValue("베토벤 (Beethoven, Ludwig van)");
    expect(screen.getByLabelText("한국어 대표 제목")).toHaveValue("월광 소나타");
    expect(screen.getByLabelText("작곡 연도")).toHaveValue("1801");
    expect(screen.getByLabelText("조성")).toHaveValue("C-sharp minor");
    expect(screen.getByLabelText("악장 구성")).toHaveValue("3 movements");
    expect(screen.getByLabelText("IMSLP 작품 페이지")).toHaveValue(
      "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
    );
    expect(screen.getByLabelText("중급")).toBeChecked();
    expect(screen.getByLabelText("보임")).toBeChecked();
    expect(screen.getByRole("button", { name: "Op.27 No.2 삭제" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "월광 삭제" })).toBeInTheDocument();
  });

  it("난이도 라디오 5개와 한 줄 기준", async () => {
    renderEdit();
    await waitLoaded();
    for (const name of ["입문", "초급", "중급", "고급", "미정"]) {
      expect(screen.getByLabelText(name)).toBeInTheDocument();
    }
    expectText("바이엘 수준");
    expectText("체르니 30 수준");
    expectText("체르니 40·소나티네 수준");
    expectText("체르니 50 이상·연주회 레퍼토리");
  });

  it("한국어 제목 도움말과 악장 페이지 안내 도움말", async () => {
    renderEdit();
    await waitLoaded();
    expectText("비워 두면 사용자에겐 원어 제목이 보이고 상태는 '보완 필요'가 돼요");
    expectText('사용자 곡 상세에 "악장 안내: … (추천 판본 기준)"으로 보여요');
    expect(screen.getByPlaceholderText(CATALOG_PLACEHOLDER)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(ALIAS_PLACEHOLDER)).toBeInTheDocument();
  });

  it("작곡가 입력칸에 타이핑하면 목록이 좁혀지고, 맨 아래에 '새 작곡가 등록'(새 탭)", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    const combobox = screen.getByLabelText("작곡가");
    await user.clear(combobox);
    await user.type(combobox, "쇼");
    expect(screen.getByRole("option", { name: "쇼팽 (Chopin, Frédéric)" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "베토벤 (Beethoven, Ludwig van)" })).not.toBeInTheDocument();

    const add = screen.getByRole("link", { name: /새 작곡가 등록/ });
    expect(add).toHaveAttribute("href", "/admin/composers/new");
    expect(add).toHaveAttribute("target", "_blank");

    await user.click(screen.getByRole("option", { name: "쇼팽 (Chopin, Frédéric)" }));
    expect(combobox).toHaveValue("쇼팽 (Chopin, Frédéric)");
  });

  it("숨김을 고르면 안내가 뜨고, 수집이 숨긴 곡이면 사유를 보여준다", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.click(screen.getByLabelText("숨김"));
    expectText("숨기면 검색·인기곡·작곡가 어디에도 나오지 않아요");

    mockFetch([
      ...baseRoutes(),
      { url: DETAIL, method: "GET", data: adminWorkDetail({ hidden: true, hiddenReason: "피아노 독주곡이 아닌 것 같아요" }) },
    ]);
    renderWithProviders(<WorkFormPage />, { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" });
    await findText('수집 시 "피아노 독주곡이 아닌 것 같아요"로 숨김 처리됨 — 확인 후 보임으로 바꿔 주세요');
  });
});

describe("WorkFormPage — 별칭 칩·겹침 경고", () => {
  it("별칭을 추가하면 겹침을 조회하고 '다른 곡 20개에도 있어요' 를 보여준다", async () => {
    const user = userEvent.setup();
    renderEdit(adminWorkDetail(), [{ url: OVERLAP, method: "GET", data: { alias: "녹턴", overlapCount: 20 } }]);
    await waitLoaded();
    await user.type(screen.getByPlaceholderText(ALIAS_PLACEHOLDER), "녹턴{Enter}");
    expect(screen.getByRole("button", { name: "녹턴 삭제" })).toBeInTheDocument();
    await waitFor(() => expect(findCall(OVERLAP)).toBeDefined());
    expect(findCall(OVERLAP).params.get("alias")).toBe("녹턴");
    expect(findCall(OVERLAP).params.get("excludeWorkId")).toBe("21");
    await findText("'녹턴'은 다른 곡 20개에도 있어요");
  });

  it("겹치는 곡이 없으면 경고가 없다", async () => {
    const user = userEvent.setup();
    renderEdit(adminWorkDetail(), [{ url: OVERLAP, method: "GET", data: { alias: "월광달빛", overlapCount: 0 } }]);
    await waitLoaded();
    await user.type(screen.getByPlaceholderText(ALIAS_PLACEHOLDER), "월광달빛{Enter}");
    await waitFor(() => expect(findCall(OVERLAP)).toBeDefined());
    expectNoText("다른 곡");
  });

  it("같은 곡 안에서 중복 별칭은 추가되지 않고 겹침 조회도 하지 않는다", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.type(screen.getByPlaceholderText(ALIAS_PLACEHOLDER), "월광{Enter}");
    expect(screen.getAllByRole("button", { name: "월광 삭제" })).toHaveLength(1);
    expect(findCalls(OVERLAP)).toHaveLength(0);
  });

  it("칩을 지우면 저장 본문에서도 빠진다", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.click(screen.getByRole("button", { name: "월광 삭제" }));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.aliases).toEqual(["Moonlight Sonata"]);
  });
});

describe("WorkFormPage — 검증·저장", () => {
  it("원어 제목이 비면 '원어 제목을 입력해 주세요', 저장 요청 없음", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.clear(screen.getByLabelText("원어 제목"));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("원어 제목을 입력해 주세요");
    expectText("입력을 확인해 주세요");
    expect(detailCalls("PUT")).toHaveLength(0);
  });

  it("작곡가를 고르지 않으면 '작곡가를 골라 주세요', 저장 요청 없음", async () => {
    const user = userEvent.setup();
    renderNew();
    await user.type(screen.getByLabelText("원어 제목"), "Nocturnes, Op.9");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("작곡가를 골라 주세요");
    expect(findCalls(COLLECTION)).toHaveLength(0);
  });

  it("한국어 제목이 비어도 저장된다 (titleKo: null)", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.clear(screen.getByLabelText("한국어 대표 제목"));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.titleKo).toBeNull();
  });

  it("수정 저장 → PUT 본문(WorkSaveDTO), 같은 화면에 머물고 '저장했어요'", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderEdit();
    await waitLoaded();
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body).toEqual({
      composerId: 4,
      titleKo: "월광 소나타",
      titleOriginal: "Piano Sonata No.14, Op.27 No.2",
      catalogNumbers: ["Op.27 No.2"],
      aliases: ["월광", "Moonlight Sonata"],
      level: "INTERMEDIATE",
      compositionYear: "1801",
      musicalKey: "C-sharp minor",
      movements: "3 movements",
      movementPageGuide: null,
      // §4-8 전체 교체 — 상세 응답(§4-7)에서 받은 값을 손대지 않았으면 그대로 되돌려 보낸다
      collectionGuide: "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요",
      imslpUrl: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
      hidden: false,
    });
    await findText("저장했어요");
    expect(getLocation().pathname).toBe("/admin/works/21");
  });

  it("등록 저장 → POST 후 /admin/works/:id 로 이동", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderNew();
    const combobox = screen.getByLabelText("작곡가");
    await user.type(combobox, "베토");
    await user.click(screen.getByRole("option", { name: "베토벤 (Beethoven, Ludwig van)" }));
    await user.type(screen.getByLabelText("원어 제목"), "Piano Sonata No.14, Op.27 No.2");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(findCalls(COLLECTION)).toHaveLength(1));
    expect(findCall(COLLECTION).body.composerId).toBe(4);
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/works/21"));
    await findText("저장했어요");
  });

  it("저장으로 보완 필요가 풀리면 '저장했어요 — 사용자 화면에서 다운로드가 열렸어요'", async () => {
    const user = userEvent.setup();
    renderEdit(
      adminWorkDetail({ status: "PREPARING", needsWork: true, missing: ["TITLE_KO"], titleKo: null }),
      [{ url: DETAIL, method: "PUT", data: adminWorkDetail({ status: "READY", needsWork: false, missing: [] }) }],
    );
    await waitLoaded();
    await user.type(screen.getByLabelText("한국어 대표 제목"), "월광 소나타");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("저장했어요 — 사용자 화면에서 다운로드가 열렸어요");
    expectNoText("빠진 것");
  });

  it("저장 실패 → '저장하지 못했어요. 잠시 후 다시 시도해 주세요'", async () => {
    const user = userEvent.setup();
    renderEdit(adminWorkDetail(), [
      { url: DETAIL, method: "PUT", status: 500, error: "INTERNAL_ERROR", message: "서버 오류" },
    ]);
    await waitLoaded();
    await user.click(screen.getByRole("button", { name: "저장" }));
    await findText("저장하지 못했어요. 잠시 후 다시 시도해 주세요");
  });
});

describe("WorkFormPage — 삭제·판본 영역", () => {
  it("판본 0개·다운로드 기록 없음 → '이 곡을 삭제할까요?' → DELETE → 목록 + '삭제했어요'", async () => {
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);
    const { getLocation } = renderEdit(adminWorkDetail({ editions: [], hasDownloadHistory: false, recommendedEditionId: null }));
    await waitLoaded();
    await user.click(workDeleteButton());
    const dialog = screen.getByRole("dialog", { name: "이 곡을 삭제할까요?" });
    expect(confirmSpy).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));
    await waitFor(() => expect(detailCalls("DELETE")).toHaveLength(1));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/works"));
    await findText("삭제했어요");
    confirmSpy.mockRestore();
  });

  it("판본이 있으면 '판본 1개와 파일이 함께 지워져요. 삭제할까요?'", async () => {
    const user = userEvent.setup();
    renderEdit(adminWorkDetail({ editions: [adminEditionRecommended], hasDownloadHistory: false }));
    await waitLoaded();
    await user.click(workDeleteButton());
    expect(screen.getByRole("dialog", { name: "판본 1개와 파일이 함께 지워져요. 삭제할까요?" })).toBeInTheDocument();
  });

  it("다운로드 기록이 있으면 숨김을 권하고 '숨김으로 바꾸기' 는 hidden=true 로 저장한다", async () => {
    const user = userEvent.setup();
    renderEdit(adminWorkDetail({ hasDownloadHistory: true }));
    await waitLoaded();
    await user.click(workDeleteButton());
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("이 곡은 다운로드 기록이 있어요. 삭제 대신 '숨김'을 권해요");
    expect(within(dialog).getByRole("button", { name: "그래도 삭제" })).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "숨김으로 바꾸기" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.hidden).toBe(true);
    expect(detailCalls("DELETE")).toHaveLength(0);
  });

  it("수정 화면에는 판본 영역이, 등록 화면에는 안내 문구가 있다", async () => {
    renderEdit();
    await waitLoaded();
    expectText("판본 (1개)");

    mockFetch([...baseRoutes(), { url: COLLECTION, method: "POST", data: adminWorkDetail(), status: 201 }]);
    renderWithProviders(<WorkFormPage />, { route: "/admin/works/new", path: "/admin/works/new", auth: "admin" });
    expectText("곡을 저장하면 판본을 추가할 수 있어요");
  });
});

describe("WorkFormPage — 변경 후 이탈 (05-E 상태별 UI)", () => {
  // 곡 편집에는 '취소' 버튼이 없으므로 화면 안의 이동(브레드크럼)이 이탈 지점이다.
  // 브라우저 뒤로가기/탭 닫기까지 막지는 않는다(라우터 교체 없이 할 수 있는 범위) — beforeunload 는 구현 재량.
  it("바꾼 게 있으면 브레드크럼 '곡 관리' 클릭 시 '저장하지 않은 변경이 있어요' 확인", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderEdit();
    await waitLoaded();
    await user.type(screen.getByLabelText("원어 제목"), "!");

    await user.click(screen.getByRole("link", { name: "곡 관리" }));
    expect(screen.getByRole("dialog", { name: "저장하지 않은 변경이 있어요" })).toBeInTheDocument();
    expect(getLocation().pathname).toBe("/admin/works/21");

    await user.click(screen.getByRole("button", { name: "계속 작성" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(getLocation().pathname).toBe("/admin/works/21");

    await user.click(screen.getByRole("link", { name: "곡 관리" }));
    await user.click(screen.getByRole("button", { name: "나가기" }));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/works"));
  });

  it("바꾼 게 없으면 확인 없이 바로 목록으로", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderEdit();
    await waitLoaded();
    await user.click(screen.getByRole("link", { name: "곡 관리" }));
    await waitFor(() => expect(getLocation().pathname).toBe("/admin/works"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 수록곡 안내(collectionGuide) — 02_API §4-7(응답) · §4-8(요청, 전체 교체) · §2-2-1(scopeNote.COLLECTION)
//
// 왜 따로 묶는가: PUT 은 전체 교체다. 화면이 §4-7 로 받은 필드를 §4-8 로 되돌려 보내지 않으면
// 관리자가 제목만 고쳐 저장해도 그 필드가 null 로 덮인다. collectionGuide 는 시드가 38곡에 넣은 값이고
// (01_ERD §6) 백필은 seed_load(COLLECTION_GUIDE, imslp_url) 기록 때문에 다시 채우지 않아 영구 유실이며,
// 검색 결과의 scopeNote.COLLECTION 판정 근거라 사용자 화면의 줄까지 함께 사라진다.
// ─────────────────────────────────────────────────────────────────────────────
const COLLECTION_GUIDE = "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요";

describe("WorkFormPage — 수록곡 안내(collectionGuide) 왕복", () => {
  it("상세 응답의 collectionGuide 가 '수록곡 안내' 칸에 채워진다", async () => {
    renderEdit();
    await waitLoaded();
    expect(screen.getByLabelText("수록곡 안내")).toHaveValue(COLLECTION_GUIDE);
  });

  it("다른 필드만 고쳐 저장해도 PUT 본문에 collectionGuide 가 그대로 실려 나간다", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.clear(screen.getByLabelText("한국어 대표 제목"));
    await user.type(screen.getByLabelText("한국어 대표 제목"), "월광");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.titleKo).toBe("월광");
    expect(detailCalls("PUT")[0].body.collectionGuide).toBe(COLLECTION_GUIDE);
  });

  it("칸을 비워 저장하면 null 로 나간다 (전체 교체 계약 — 지울 수단이 있어야 한다)", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.clear(screen.getByLabelText("수록곡 안내"));
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.collectionGuide).toBeNull();
  });

  it("공백만 남겨도 null 로 나간다 (다른 문자열 필드와 같은 정규화)", async () => {
    const user = userEvent.setup();
    renderEdit();
    await waitLoaded();
    await user.clear(screen.getByLabelText("수록곡 안내"));
    await user.type(screen.getByLabelText("수록곡 안내"), "   ");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.collectionGuide).toBeNull();
  });

  it("collectionGuide 가 null 인 곡(수집으로 들어온 곡)에 적어 넣으면 그 값이 저장된다", async () => {
    const user = userEvent.setup();
    renderEdit(adminWorkDetail({ collectionGuide: null }));
    await waitLoaded();
    expect(screen.getByLabelText("수록곡 안내")).toHaveValue("");
    await user.type(screen.getByLabelText("수록곡 안내"), "'강아지 왈츠'가 들어 있는 왈츠 모음이에요");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.collectionGuide).toBe("'강아지 왈츠'가 들어 있는 왈츠 모음이에요");
  });

  it("'숨김으로 바꾸기'(삭제 대신 저장)에서도 collectionGuide 가 보존된다", async () => {
    const user = userEvent.setup();
    renderEdit(adminWorkDetail({ hasDownloadHistory: true }));
    await waitLoaded();
    await user.click(workDeleteButton());
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "숨김으로 바꾸기" }));
    await waitFor(() => expect(detailCalls("PUT")).toHaveLength(1));
    expect(detailCalls("PUT")[0].body.hidden).toBe(true);
    expect(detailCalls("PUT")[0].body.collectionGuide).toBe(COLLECTION_GUIDE);
  });

  it("새 곡 등록 본문에도 collectionGuide 키가 있다 (미입력이면 null)", async () => {
    const user = userEvent.setup();
    renderNew();
    await user.type(screen.getByLabelText("작곡가"), "베토");
    await user.click(screen.getByRole("option", { name: "베토벤 (Beethoven, Ludwig van)" }));
    await user.type(screen.getByLabelText("원어 제목"), "Piano Sonata No.14, Op.27 No.2");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(findCalls(COLLECTION)).toHaveLength(1));
    expect(findCall(COLLECTION).body).toHaveProperty("collectionGuide");
    expect(findCall(COLLECTION).body.collectionGuide).toBeNull();
  });

  /*
   * 도움말 (기획 §3 F5-2 · §12-2, 인수 조건 §6 — qa 5차 결함 2. senior-dev 가 문구 확정, 2026-09-09).
   *
   * 이 칸은 **관리자가 무엇을 적어야 하는지 스스로 알 수 없는 칸**이다: 라벨 "수록곡 안내" 만으로는
   * 쪽수를 적는지 곡 번호를 적는지, 왜 적어야 하는지 알 수 없다. 그리고 이 값 하나가 사용자 화면에서
   * 하는 일이 크다 — 있으면 그 곡이 "묶음 악보" 가 되어 난이도·쪽수에 "(전곡 기준)" 이 붙고(§2-2-1 COLLECTION),
   * 검색 결과의 "'…'가 들어 있는 악보" 줄이 생긴다. 바로 위 "악장 페이지 안내" 에는 도움말이 있어 대비도 뚜렷했다.
   *
   * 문구는 기획 §3 F5-2 가 이미 따옴표로 적어 둔 문장을 제품 말투(…어요)로 맞춘 것이다.
   * 인수 조건 §6 이 "취지의 도움말" 로 열어 뒀으므로 문장 끝만 맞췄다(designer 가 다르게 정하면 이 상수를 고친다).
   */
  const COLLECTION_GUIDE_HELP =
    '여러 곡·여러 악장이 한 PDF 에 들어 있는 곡이면 적어 주세요 — 이 줄이 있어야 사용자 화면에 "(전곡 기준)"이 붙어요';

  it("입력칸에 도움말이 있다 — 무엇을 적는 칸인지와 이 값이 사용자 화면에서 하는 일", async () => {
    renderEdit();
    await waitLoaded();

    expectText(COLLECTION_GUIDE_HELP);
  });

  it("그 도움말은 '수록곡 안내' 칸 옆에 있다 — 다른 칸의 설명으로 읽히면 안 된다", async () => {
    renderEdit();
    await waitLoaded();

    const group = screen.getByLabelText("수록곡 안내").closest(".form-group");
    expect(group).not.toBeNull();
    expect(group.textContent.replace(/s+/g, " ")).toContain(COLLECTION_GUIDE_HELP);
  });

  it("새 곡 등록 화면에도 같은 도움말이 있다 — 수집으로 들어온 곡을 채우는 자리도 여기다", async () => {
    renderNew();
    await findText("새 곡");

    expectText(COLLECTION_GUIDE_HELP);
  });

  it("수록곡 안내만 고쳐도 '바뀐 게 있다'로 보고 이탈을 막는다 (05-E)", async () => {
    const user = userEvent.setup();
    const { getLocation } = renderEdit();
    await waitLoaded();
    await user.type(screen.getByLabelText("수록곡 안내"), "!");
    await user.click(screen.getByRole("link", { name: "곡 관리" }));
    expect(screen.getByRole("dialog", { name: "저장하지 않은 변경이 있어요" })).toBeInTheDocument();
    expect(getLocation().pathname).toBe("/admin/works/21");
  });
});
