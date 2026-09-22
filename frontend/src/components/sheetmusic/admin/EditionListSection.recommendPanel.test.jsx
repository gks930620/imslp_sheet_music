import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditionListSection } from "./EditionListSection.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../../test/apiMock.js";
import {
  adminEditionCandidate,
  adminEditionNoFile,
  adminEditionRecommended,
  autoRecommendationLog,
  recommendationBlock,
} from "../../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../../test/text.js";

/**
 * 판본 목록과 새 두 영역이 만나는 자리 — 화면정의 `06_관리자_판본관리.md` A-0 · A-2 (계약 02 §5-6 · §5-6-2 · §4-7-2).
 *
 * <p><b>이 파일이 막는 회귀 하나</b>: "추천으로 지정" 은 더 이상 <b>즉시 적용되지 않는다.</b>
 * 지금 코드는 누르는 순간 {@code PUT} 을 보내는데, 그러면 사유를 물을 자리가 사라진다(기획 §3-1).
 *
 * <p>그리고 화면정의 A-2 개정: <b>지정한 뒤 행 아래에 개별 경고 상자를 더 두지 않는다.</b>
 * 경고는 <b>바꾸기 전에</b> A-3 안에서 읽는 것이고, 바꾼 뒤 남아야 할 말은 섹션 위 한 줄뿐이다.
 */
const PREVIEW = /\/api\/admin\/works\/21\/recommended-edition\/preview\?/;
const RECOMMEND = /\/api\/admin\/works\/21\/recommended-edition$/;

const composer = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827 };

function renderSection({
  editions = [adminEditionRecommended, adminEditionCandidate],
  recommendedEditionId = 301,
  candidateEditionId = 302,
  recommendation = recommendationBlock({ current: autoRecommendationLog() }),
  routes = [],
} = {}) {
  const onChanged = vi.fn();
  const onToast = vi.fn();
  mockFetch([
    { url: PREVIEW, data: { workId: 21, editionId: 302, warnings: [] } },
    {
      url: RECOMMEND,
      method: "PUT",
      data: {
        workId: 21, previousEditionId: recommendedEditionId, editionId: 302,
        workStatus: "READY", recommendationReviewed: false, warnings: [],
      },
    },
    ...routes,
  ]);
  const utils = renderWithProviders(
    <EditionListSection
      workId={21}
      editions={editions}
      recommendedEditionId={recommendedEditionId}
      candidateEditionId={candidateEditionId}
      recommendationReviewed={false}
      recommendation={recommendation}
      composer={composer}
      onChanged={onChanged}
      onToast={onToast}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
  return { ...utils, onChanged, onToast };
}

const row = (id) => screen.getByTestId(`admin-edition-row-${id}`);

describe("추천으로 지정 → 패널 (화면정의 A-2 · A-3)", () => {
  it("누르면 즉시 바꾸지 않고 사유를 고르는 자리가 열린다 (기획 §3-1)", async () => {
    const user = userEvent.setup();
    renderSection();

    await user.click(within(row(302)).getByRole("button", { name: "이 후보를 추천으로 지정" }));

    await findText("이 판본으로 바꾸기");
    expect(findCalls(RECOMMEND)).toHaveLength(0);
  });

  it("패널이 열린 동안 그 버튼은 펼침 상태로 표시된다", async () => {
    const user = userEvent.setup();
    renderSection();
    const button = within(row(302)).getByRole("button", { name: "이 후보를 추천으로 지정" });

    await user.click(button);

    await waitFor(() => expect(button).toHaveAttribute("aria-expanded", "true"));
  });

  it("파일 없는 행은 버튼이 막혀 패널이 열리지 않는다 (8-C 5 — 이 규칙은 그대로다)", async () => {
    renderSection({ editions: [adminEditionRecommended, adminEditionNoFile], candidateEditionId: null });

    expect(within(row(304)).getByRole("button", { name: "추천으로 지정" })).toBeDisabled();
    expectText("파일이 없어 추천으로 지정할 수 없어요");
    expectNoText("이 판본으로 바꾸기");
  });

  it("성공하면 기존 Toast 문구 그대로 알리고 곡을 다시 부른다", async () => {
    const user = userEvent.setup();
    const { onChanged, onToast } = renderSection();

    await user.click(within(row(302)).getByRole("button", { name: "이 후보를 추천으로 지정" }));
    await screen.findByRole("radio", { name: /이 판본이 더 읽기 좋아요/ });
    await user.click(screen.getByRole("radio", { name: /이 판본이 더 읽기 좋아요/ }));
    await user.click(screen.getByRole("button", { name: "이 판본으로 바꾸기" }));

    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    expect(onToast).toHaveBeenCalledWith(expect.stringContaining("추천 판본을"));
    expect(onToast).toHaveBeenCalledWith(expect.stringContaining("바꿨어요"));
  });

  it("바꾼 뒤 행 아래에 경고 상자를 남기지 않는다 — 경고는 바꾸기 전에 읽는 것이다 (화면정의 A-2 개정)", async () => {
    const user = userEvent.setup();
    renderSection({
      routes: [
        { url: PREVIEW, data: { workId: 21, editionId: 302, warnings: ["ARRANGEMENT", "PARTIAL_SCOPE"] } },
        {
          url: RECOMMEND, method: "PUT",
          data: {
            workId: 21, previousEditionId: 301, editionId: 302, workStatus: "READY",
            recommendationReviewed: false, warnings: ["ARRANGEMENT", "PARTIAL_SCOPE"],
          },
        },
      ],
    });

    await user.click(within(row(302)).getByRole("button", { name: "이 후보를 추천으로 지정" }));
    await screen.findByRole("radio", { name: /이 판본이 더 읽기 좋아요/ });
    await user.click(screen.getByRole("radio", { name: /이 판본이 더 읽기 좋아요/ }));
    await user.click(screen.getByRole("button", { name: "이 판본으로 바꾸기" }));

    await waitFor(() => expect(findCalls(RECOMMEND)).toHaveLength(1));
    // 패널이 닫히면 그 안의 경고도 함께 사라진다 — 같은 말을 행 아래에 다시 쌓지 않는다
    await waitFor(() => expectNoText("이 판본으로 바꾸기"));
    expectNoText("사용자가 원곡 악보를 기대하고 받을 수 있어요");
  });

  it("취소하면 패널만 닫히고 아무것도 보내지 않는다 (8-B 6)", async () => {
    const user = userEvent.setup();
    const { onChanged } = renderSection();

    await user.click(within(row(302)).getByRole("button", { name: "이 후보를 추천으로 지정" }));
    await screen.findByRole("button", { name: "취소" });
    await user.click(screen.getByRole("button", { name: "취소" }));

    await waitFor(() => expectNoText("이 판본으로 바꾸기"));
    expect(findCalls(RECOMMEND)).toHaveLength(0);
    expect(onChanged).not.toHaveBeenCalled();
  });
});

describe("A-1 상자의 자리 (화면정의 A-0)", () => {
  it("추천이 있으면 판본 목록 '위' 에 고른 이유 상자가 있다", async () => {
    renderSection();

    await findText("이 판본을 고른 이유");
    const box = screen.getByText("이 판본을 고른 이유");
    const list = screen.getByText(/^판본 \(/);
    // 섹션 안에서 목록 제목보다 뒤, 판본 행보다 앞 (DOM 순서로 확인한다)
    expect(list.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(box.compareDocumentPosition(row(301)) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("추천이 없으면 상자가 없고 기존 안내만 남는다 (8-A 6)", async () => {
    renderSection({
      editions: [adminEditionCandidate],
      recommendedEditionId: null,
      recommendation: recommendationBlock(),
    });

    await findText("추천 판본이 없어요");
    expectNoText("이 판본을 고른 이유");
  });
});
