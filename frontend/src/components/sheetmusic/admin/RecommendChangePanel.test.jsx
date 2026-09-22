import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RecommendChangePanel } from "./RecommendChangePanel.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../../test/apiMock.js";
import {
  adminEdition,
  adminEditionCandidate,
  adminEditionRecommended,
  adminRecommendationLog,
  autoRecommendationLog,
} from "../../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../../test/text.js";

/**
 * 화면 A-3 "이 판본으로 바꾸기" 패널 — 화면정의 `06_관리자_판본관리.md` A-3, 계약 02 §5-6 · §5-6-2.
 *
 * <p>"추천으로 지정" 은 이제 <b>즉시 적용되지 않는다.</b> 이 패널을 한 번 거친다(기획 §3-1).
 * 여기서 잠그는 계약 넷:
 * <ol>
 *   <li><b>경고를 바꾸기 전에 읽는다</b> — 서버(§5-6-2)가 6종을 계산해 주고, 화면은 그것을 두 묶음으로 나눠 상자 <b>하나</b>에 담는다.
 *       노란 상자 5개는 아무도 안 읽는다(화면정의 A-3 ②).</li>
 *   <li><b>사유는 필수</b>다. 안 고르면 저장하지 않고 안내한다 — 버튼을 {@code disabled} 로 두지 않는다
 *       (비활성 버튼은 "왜 못 누르는지" 를 말하지 않는다).</li>
 *   <li><b>"기타" 면 메모가 필수</b>다.</li>
 *   <li><b>"확인했어요" 체크는 기본 꺼짐</b>이고, 켜면 그 자리에서 검수가 끝난다({@code reviewed: true}).</li>
 * </ol>
 */
const PREVIEW = /\/api\/admin\/works\/21\/recommended-edition\/preview\?/;
const RECOMMEND = /\/api\/admin\/works\/21\/recommended-edition$/;

const target = adminEditionCandidate;
/** 경고 ②③이 함께 뜨는 판본 — 편곡 + 2악장만. 문구에 악장 번호가 들어가므로 판본이 그 값을 갖고 있어야 한다. */
const arrangedMovement = adminEdition({
  id: 302, isRecommended: false, isCandidate: true, kind: "ARRANGEMENT", scope: "MOVEMENT", movementNumber: 2,
});

function renderPanel({
  warnings = [],
  edition = target,
  currentEdition = adminEditionRecommended,
  currentReason = autoRecommendationLog(),
  routes = [],
} = {}) {
  const onCancel = vi.fn();
  const onDone = vi.fn();
  mockFetch([
    { url: PREVIEW, data: { workId: 21, editionId: edition.id, warnings } },
    {
      url: RECOMMEND,
      method: "PUT",
      data: {
        workId: 21, previousEditionId: currentEdition?.id ?? null, editionId: edition.id,
        workStatus: "READY", recommendationReviewed: false, warnings,
      },
    },
    ...routes,
  ]);
  const utils = renderWithProviders(
    <RecommendChangePanel
      workId={21}
      edition={edition}
      currentEdition={currentEdition}
      currentReason={currentReason}
      onCancel={onCancel}
      onDone={onDone}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
  return { ...utils, onCancel, onDone };
}

async function submit(user) {
  await user.click(screen.getByRole("button", { name: "이 판본으로 바꾸기" }));
}

describe("① '지금 추천' 한 줄 (기획 §2-4 — 창을 닫고 위로 스크롤하게 만들지 않는다)", () => {
  it("지금 추천 판본과 그 고른 이유가 한 줄로 함께 보인다 (8-B 1)", async () => {
    renderPanel();

    await findText("지금 추천");
    expectText("Breitkopf");
    expectText("자동으로 골랐어요");
    expectText("IMSLP 1,204회");
    expectText("후보 3개 중 1위");
  });

  it("사람이 고른 추천이면 닉네임과 사유 라벨 한 줄이다", async () => {
    renderPanel({ currentReason: adminRecommendationLog() });

    await findText("창희 님이 골랐어요");
    expectText("앞 추천이 이 곡의 악보가 아니었어요");
  });

  it("기록이 없으면 그렇게 말한다 — 지어내지 않는다", async () => {
    renderPanel({ currentReason: null });

    await findText("기록이 없어요");
    expectNoText("자동으로 골랐어요");
  });

  it("추천이 아직 없으면 '이 곡의 첫 추천이에요'", async () => {
    renderPanel({ currentEdition: null, currentReason: null });

    await findText("이 곡의 첫 추천이에요");
  });
});

describe("② 경고 묶음 상자 (§5-6-2, 화면정의 A-3 ②)", () => {
  it("열릴 때 §5-6-2 로 경고를 미리 묻는다 — 화면이 직접 세지 않는다", async () => {
    renderPanel({ warnings: ["PARTIAL_SCOPE"] });

    await waitFor(() => expect(findCalls(PREVIEW)).toHaveLength(1));
    expect(findCalls(PREVIEW)[0].method).toBe("GET");
    expect(findCalls(PREVIEW)[0].params.get("editionId")).toBe(String(target.id));
  });

  it("해당되는 것이 없으면 경고 상자가 아예 없다", async () => {
    renderPanel({ warnings: [] });

    await waitFor(() => expect(findCalls(PREVIEW)).toHaveLength(1));
    expectNoText("바꾸기 전에 확인할 것");
  });

  it("1개뿐이면 제목 줄·묶음 제목 없이 한 줄만 보인다 — 하나짜리에 소제목을 붙이면 관료적으로 읽힌다", async () => {
    renderPanel({ edition: arrangedMovement, warnings: ["ARRANGEMENT"] });

    await findText("이 판본은 편곡이에요");
    expectNoText("바꾸기 전에 확인할 것");
    expectNoText("이 판본은 이런 판본이에요");
    expectNoText("바뀌면 생기는 일");
  });

  it("여럿이면 상자 하나에 개수와 두 묶음으로 담는다 (8-C 1 — 전부 보인다)", async () => {
    renderPanel({
      edition: arrangedMovement,
      warnings: ["WORK_BECOMES_CLOSED", "HAS_DOWNLOAD_HISTORY", "NOT_DOWNLOADABLE", "ARRANGEMENT", "PARTIAL_SCOPE"],
    });

    await findText("바꾸기 전에 확인할 것 5가지");
    expectText("바뀌면 생기는 일");
    expectText("이 판본은 이런 판본이에요");
    expectText("지금 받을 수 있는 곡이에요 — 바꾸면 이 곡의 다운로드가 닫혀요");
    expectText("이미 이 곡을 받아 간 사람이 있어요");
    expectText("사용자에게 다운로드가 열리지 않아요");
    expectText("이 판본은 편곡이에요");
    expectText("2악장만 들어 있어요");
  });

  it("④와 ①은 겹쳐 보여도 둘 다 남는다 — 하나는 판본의 성질, 하나는 이 변경의 결과다 (8-C 2)", async () => {
    renderPanel({ warnings: ["WORK_BECOMES_CLOSED", "NOT_DOWNLOADABLE"] });

    await findText("바꾸기 전에 확인할 것 2가지");
    expectText("지금 받을 수 있는 곡이에요 — 바꾸면 이 곡의 다운로드가 닫혀요");
    expectText("사용자에게 다운로드가 열리지 않아요");
  });

  it("한 묶음만 해당되면 그 묶음 제목은 생략한다", async () => {
    renderPanel({ edition: arrangedMovement, warnings: ["NOT_DOWNLOADABLE", "PARTIAL_SCOPE"] });

    await findText("바꾸기 전에 확인할 것 2가지");
    expectNoText("바뀌면 생기는 일");
    expectNoText("이 판본은 이런 판본이에요");
  });

  it("파트보 경고를 그린다 (8-C 4) — 편곡과 같은 자리를 쓴다", async () => {
    renderPanel({ warnings: ["PARTS"], edition: adminEdition({ id: 306, isRecommended: false, kind: "PARTS" }) });

    await findText("이 판본은 한 악기 파트만 담고 있어요");
  });

  it("경고를 접지 않는다 — '더 보기' 뒤에 숨은 것이 '곡이 닫혀요' 일 수 있다", async () => {
    renderPanel({
      edition: arrangedMovement,
      warnings: ["WORK_BECOMES_CLOSED", "HAS_DOWNLOAD_HISTORY", "NOT_DOWNLOADABLE", "ARRANGEMENT", "PARTIAL_SCOPE"],
    });

    await findText("바꾸기 전에 확인할 것 5가지");
    expect(screen.queryByRole("button", { name: /더 보기|개 더/ })).not.toBeInTheDocument();
  });

  it("경고를 못 불러와도 패널은 열려 있고 바꿀 수 있다 — 경고는 안내이지 게이트가 아니다", async () => {
    const user = userEvent.setup();
    renderPanel({ routes: [{ url: PREVIEW, status: 500, error: "INTERNAL_ERROR" }] });

    await screen.findByRole("button", { name: "이 판본으로 바꾸기" });
    await user.click(screen.getByRole("radio", { name: /이 판본이 더 읽기 좋아요/ }));
    await submit(user);
    await waitFor(() => expect(findCalls(RECOMMEND)).toHaveLength(1));
  });
});

describe("③ 고른 사유 (필수, 하나)", () => {
  it("여섯 개가 세로로 나오고 기본 선택이 없다", async () => {
    renderPanel();

    await findText("왜 이 판본을 고르셨어요?");
    expect(screen.getAllByRole("radio")).toHaveLength(6);
    screen.getAllByRole("radio").forEach((radio) => expect(radio).not.toBeChecked());
  });

  it("첫 지정(추천이 없는 곡)에서는 '앞 추천…' 두 개를 감춘다 — 앞 추천이 없는데 고르면 근거가 거짓이 된다", async () => {
    renderPanel({ currentEdition: null, currentReason: null });

    await findText("왜 이 판본을 고르셨어요?");
    expect(screen.getAllByRole("radio")).toHaveLength(4);
    expectNoText("앞 추천이 이 곡의 악보가 아니었어요");
    expectNoText("앞 추천은 지금 받을 수 없어요");
  });

  it("사유를 고르지 않고 누르면 저장하지 않고 안내한다 (8-B 2) — 버튼을 비활성으로 두지 않는다", async () => {
    const user = userEvent.setup();
    const { onDone } = renderPanel();

    await screen.findByRole("button", { name: "이 판본으로 바꾸기" });
    expect(screen.getByRole("button", { name: "이 판본으로 바꾸기" })).toBeEnabled();
    await submit(user);

    await findText("왜 이 판본을 골랐는지 골라 주세요");
    expect(findCalls(RECOMMEND)).toHaveLength(0);
    expect(onDone).not.toHaveBeenCalled();
  });
});

describe("④ 메모", () => {
  it("'기타' 가 아니면 메모를 비워도 보낸다 (8-B 3)", async () => {
    const user = userEvent.setup();
    renderPanel();

    await screen.findByRole("radio", { name: /이 판본이 더 읽기 좋아요/ });
    await user.click(screen.getByRole("radio", { name: /이 판본이 더 읽기 좋아요/ }));
    await submit(user);

    await waitFor(() => expect(findCalls(RECOMMEND)).toHaveLength(1));
    expect(findCalls(RECOMMEND)[0].body).toEqual({
      editionId: target.id, reason: "BETTER_READABILITY", note: null, reviewed: false,
    });
  });

  it("'기타' 인데 메모가 비면 저장하지 않고 안내한다 (8-B 4)", async () => {
    const user = userEvent.setup();
    renderPanel();

    await screen.findByRole("radio", { name: /기타/ });
    await user.click(screen.getByRole("radio", { name: /기타/ }));
    await submit(user);

    await findText("왜 이 판본을 골랐는지 적어 주세요");
    expect(findCalls(RECOMMEND)).toHaveLength(0);
  });

  it("'기타' 를 고르면 메모가 필수 라벨로 바뀌고, 적으면 보내진다", async () => {
    const user = userEvent.setup();
    renderPanel();

    await screen.findByRole("radio", { name: /기타/ });
    expectText("메모 (선택)");
    await user.click(screen.getByRole("radio", { name: /기타/ }));
    expectNoText("메모 (선택)");

    await user.type(screen.getByLabelText(/메모/), "직접 적은 이유");
    await submit(user);

    await waitFor(() => expect(findCalls(RECOMMEND)).toHaveLength(1));
    expect(findCalls(RECOMMEND)[0].body.note).toBe("직접 적은 이유");
  });

  it("메모는 300자를 넘겨 칠 수 없고 남은 글자 수를 보여준다 (§0-6)", async () => {
    const user = userEvent.setup();
    renderPanel();

    await screen.findByLabelText(/메모/);
    expect(screen.getByLabelText(/메모/)).toHaveAttribute("maxLength", "300");
    await user.type(screen.getByLabelText(/메모/), "가나다");
    expectText("3/300");
  });
});

describe("⑤ '확인했어요' 체크 (기본 꺼짐 — 기획 §3-3)", () => {
  it("기본은 꺼짐이고 체크하지 않으면 미검수로 남는다고 말한다", async () => {
    renderPanel();

    const checkbox = await screen.findByRole("checkbox", {
      name: /미리보기를 열어 이 곡의 피아노 악보가 맞는지 확인했어요/,
    });
    expect(checkbox).not.toBeChecked();
    expectText("체크하지 않으면 이 곡은 '추천 판본 확인 필요'에 남아요");
  });

  it("체크하면 reviewed: true 로 함께 보낸다 — 방금 본 것을 또 보러 가게 만들지 않는다", async () => {
    const user = userEvent.setup();
    renderPanel();

    await screen.findByRole("radio", { name: /이 판본이 더 읽기 좋아요/ });
    await user.click(screen.getByRole("radio", { name: /이 판본이 더 읽기 좋아요/ }));
    await user.click(screen.getByRole("checkbox", { name: /확인했어요/ }));
    expectText("이 곡은 '추천 판본 확인 필요'에서 바로 빠져요");
    await submit(user);

    await waitFor(() => expect(findCalls(RECOMMEND)).toHaveLength(1));
    expect(findCalls(RECOMMEND)[0].body.reviewed).toBe(true);
  });

  it("체크 옆의 '미리보기 열기' 는 바꿀 판본의 미리보기다 — 보려고 화면을 떠나게 하지 않는다", async () => {
    renderPanel({ edition: adminEdition({ id: 306, isRecommended: false, previewUrl: "/uploads/preview-306.png" }) });

    expect(await screen.findByRole("button", { name: "미리보기 열기" })).toBeEnabled();
  });

  it("바꿀 판본에 미리보기가 없으면 버튼이 막히고 이유를 말한다", async () => {
    renderPanel({ edition: adminEdition({ id: 306, isRecommended: false, previewUrl: null }) });

    expect(await screen.findByRole("button", { name: "미리보기 열기" })).toBeDisabled();
    expectText("미리보기가 아직 없어요");
  });
});

describe("⑥ 버튼", () => {
  it("취소하면 아무것도 보내지 않고 닫는다 — 확인창도 띄우지 않는다 (8-B 6)", async () => {
    const user = userEvent.setup();
    const { onCancel } = renderPanel();

    await screen.findByRole("button", { name: "취소" });
    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(onCancel).toHaveBeenCalled();
    expect(findCalls(RECOMMEND)).toHaveLength(0);
    expectNoText("작성 중인 내용이 사라져요");
  });

  it("성공하면 onDone 에 §5-6 응답을 그대로 넘긴다 — Toast·재조회는 부모의 일이다 (8-B 5)", async () => {
    const user = userEvent.setup();
    const { onDone } = renderPanel({ warnings: ["PARTIAL_SCOPE"] });

    await screen.findByRole("radio", { name: /이 판본이 곡 전체를 담고 있어요/ });
    await user.click(screen.getByRole("radio", { name: /이 판본이 곡 전체를 담고 있어요/ }));
    await submit(user);

    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(onDone.mock.calls[0][0]).toMatchObject({ workId: 21, editionId: target.id });
  });

  it("실패하면 패널이 열린 채로 남고 고른 사유·메모가 지워지지 않는다", async () => {
    const user = userEvent.setup();
    const { onDone } = renderPanel({
      routes: [{ url: RECOMMEND, method: "PUT", status: 500, error: "INTERNAL_ERROR" }],
    });

    await screen.findByRole("radio", { name: /기타/ });
    await user.click(screen.getByRole("radio", { name: /기타/ }));
    await user.type(screen.getByLabelText(/메모/), "적어 둔 이유");
    await submit(user);

    await findText("바꾸지 못했어요");
    expect(onDone).not.toHaveBeenCalled();
    expect(screen.getByRole("radio", { name: /기타/ })).toBeChecked();
    expect(screen.getByLabelText(/메모/)).toHaveValue("적어 둔 이유");
  });

  it("서버가 400 으로 막으면 그 문구를 그대로 보여준다 — 파일 없는 판본 등", async () => {
    const user = userEvent.setup();
    renderPanel({
      routes: [{
        url: RECOMMEND, method: "PUT", status: 400,
        error: "BUSINESS_RULE_VIOLATION", message: "파일이 없어 추천으로 지정할 수 없어요",
      }],
    });

    await screen.findByRole("radio", { name: /이 판본이 더 읽기 좋아요/ });
    await user.click(screen.getByRole("radio", { name: /이 판본이 더 읽기 좋아요/ }));
    await submit(user);

    await findText("파일이 없어 추천으로 지정할 수 없어요");
  });
});
