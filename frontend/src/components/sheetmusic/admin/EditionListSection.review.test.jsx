import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditionListSection } from "./EditionListSection.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../../test/apiMock.js";
import { adminEditionCandidate, adminEditionRecommended } from "../../../test/fixtures.js";
import { expectNoText, expectText } from "../../../test/text.js";

/**
 * 추천 판본 "확인함" 토글 — 02_API_명세서 §5-6-1, 기획 §3 F6-4 · §8-17 · §10-8
 * (frontend-dev 작성. 계약은 senior-dev 가 확정했고 백엔드 엔드포인트는 아직 없다 — 화면은 계약대로 부른다).
 *
 * <p>자동 지정된 추천은 관현악 총보일 수 있다. 관리자가 <b>미리보기를 열어 피아노 악보가 맞는지 본 뒤</b>
 * "확인함" 을 누르는 자리가 판본 목록의 추천 행이다 — 확인할 대상이 눈앞에 있는 곳.
 *
 * <p>확인은 "그 판본" 이 아니라 "지금 추천" 에 붙는 상태라, 추천이 없는 곡에는 이 버튼이 없다.
 */
const REVIEW = /\/api\/admin\/works\/21\/recommended-edition\/review$/;
const composer = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827 };
const editions = [adminEditionRecommended, adminEditionCandidate];

function renderSection({ recommendedEditionId = 301, recommendationReviewed = false, routes = [] } = {}) {
  const onChanged = vi.fn();
  const onToast = vi.fn();
  mockFetch([{ url: REVIEW, method: "PUT", data: { id: 21, recommendationReviewed: true } }, ...routes]);
  const utils = renderWithProviders(
    <EditionListSection
      workId={21}
      editions={editions}
      recommendedEditionId={recommendedEditionId}
      candidateEditionId={null}
      composer={composer}
      recommendationReviewed={recommendationReviewed}
      onChanged={onChanged}
      onToast={onToast}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
  return { ...utils, onChanged, onToast };
}

function recommendedRow() {
  return screen.getByTestId("admin-edition-row-301");
}

describe("EditionListSection — 추천 판본 확인함 (02 §5-6-1)", () => {
  it("미검수면 추천 행에 '확인함' 버튼이 있다", () => {
    renderSection();

    expect(within(recommendedRow()).getByRole("button", { name: "확인함" })).toBeInTheDocument();
  });

  it("누르면 reviewed: true 로 보내고 곡을 다시 불러온다", async () => {
    const user = userEvent.setup();
    const { onChanged, onToast } = renderSection();
    await user.click(within(recommendedRow()).getByRole("button", { name: "확인함" }));

    await waitFor(() => expect(findCalls(REVIEW)).toHaveLength(1));
    expect(findCalls(REVIEW)[0].method).toBe("PUT");
    expect(findCalls(REVIEW)[0].body).toEqual({ reviewed: true });
    expect(onChanged).toHaveBeenCalled();
    expect(onToast).toHaveBeenCalled();
  });

  it("확인이 끝난 곡은 표식과 '확인 해제' 로 바뀐다", () => {
    renderSection({ recommendationReviewed: true });

    expect(within(recommendedRow()).queryByRole("button", { name: "확인함" })).not.toBeInTheDocument();
    expect(within(recommendedRow()).getByRole("button", { name: "확인 해제" })).toBeInTheDocument();
    expectText("확인함");
  });

  it("'확인 해제' 는 reviewed: false 로 보낸다 — 추천을 다시 볼 수 있어야 한다", async () => {
    const user = userEvent.setup();
    renderSection({ recommendationReviewed: true });
    await user.click(within(recommendedRow()).getByRole("button", { name: "확인 해제" }));

    await waitFor(() => expect(findCalls(REVIEW)).toHaveLength(1));
    expect(findCalls(REVIEW)[0].body).toEqual({ reviewed: false });
  });

  it("추천 판본이 없으면 확인 버튼도 없다 — 확인할 대상이 없다", () => {
    renderSection({ recommendedEditionId: null });

    expectNoText("확인함");
  });

  it("추천이 아닌 행에는 확인 버튼이 없다", () => {
    renderSection();

    expect(within(screen.getByTestId("admin-edition-row-302")).queryByRole("button", { name: "확인함" })).toBeNull();
  });

  it("실패하면 그 행에 안내가 뜬다", async () => {
    const user = userEvent.setup();
    renderSection({ routes: [{ url: REVIEW, method: "PUT", status: 500, error: "INTERNAL_ERROR" }] });
    await user.click(within(recommendedRow()).getByRole("button", { name: "확인함" }));

    await waitFor(() => expectText("처리하지 못했어요"));
  });
});
